# SSE 流式回复处理流程详解

本文档梳理 `zuo-ai-agent` 中 SSE（Server-Sent Events）流式回复的完整处理链路，涵盖请求入口、模型路由、熔断保护、首包探测、数据推送到连接关闭的每一个环节。

---

## 一、一句话总结

客户端发送 GET 请求后，服务端立刻返回一个长连接 `SseEmitter`，后台异步订阅 Spring AI 的 `Flux<String>` 流，逐 chunk 推送给客户端，同时通过**首包探测**决定是否切换备用模型。

> **请求 → Controller 创建 SseEmitter → ChatService → RoutingChatService 路由 → ChatModelFactory 取候选 → ChatCircuitBreaker 熔断检查 → Spring AI ChatClient.stream() → Flux<String> 订阅 → 首包探测 → SseEmitter 逐 chunk 推送 → 完成/切换候选**

---

## 二、整体调用链

```
GET /api/chat/stream?prompt=xxx
  └─ ChatController.streamChat()
       │  创建 SseEmitter(60s)，立即返回给 HTTP 层
       └─ ChatServiceImpl.streamChat()
            └─ RoutingChatService.streamChat()
                 │  遍历 ChatModelFactory 候选队列
                 └─ tryStreamWithEntry(entry, ...)
                      │  ChatCircuitBreaker.allowCall()  ← 熔断检查
                      │  buildSpec() 构建 ChatClientRequestSpec
                      │  ChatClient.stream().content()   ← 返回 Flux<String>
                      └─ flux.subscribe(
                             onNext   → 首包探测 + SseEmitter.send(chunk)
                             onError  → markFailure + 切换候选 or completeWithError
                             onComplete → emitter.complete()
                         )
```

RAG 流式链路在此基础上多一个步骤：

```
GET /api/chat/stream/withRag?prompt=xxx&name=xxx&major=xxx
  └─ ChatController.streamChatWithRag()
       └─ ChatServiceImpl.streamChatWithRag()
            │  PromptTemplate 渲染 systemPrompt（注入 name/major）
            └─ RoutingChatService.streamChat(prompt, conId, systemPrompt, vectorStore, emitter)
                 └─ buildSpec() 中额外追加 QuestionAnswerAdvisor(vectorStore)
                      ← 向量检索结果拼入上下文后再流式推送
```

---

## 三、各层职责详解

### 3.1 Controller 层 — 创建并返回 SseEmitter

```java
// ChatController.java
@GetMapping(value = "/stream", produces = "text/event-stream;charset=UTF-8")
public SseEmitter streamChat(@RequestParam String prompt,
                             @RequestParam(required = false) String conversationId) {
    String conId = StrUtil.isBlank(conversationId) ? IdUtil.getSnowflakeNextIdStr() : conversationId;
    SseEmitter emitter = new SseEmitter(60_000L);  // 60 秒超时
    chatService.streamChat(prompt, conId, emitter); // 异步，立即返回
    return emitter;  // Spring MVC 持有连接，等待后续 send/complete
}
```

**关键点**：
- `produces = "text/event-stream;charset=UTF-8"` 告诉浏览器/客户端这是 SSE 响应
- `SseEmitter(60_000L)` 设置 60 秒超时，超时后连接自动断开
- `chatService.streamChat()` 是**异步发起**的，不会阻塞 HTTP 线程
- 方法返回 `emitter` 对象后，Spring MVC 保持该 HTTP 连接开放，等待后续通过 `emitter.send()` 写入数据

---

### 3.2 Service 层 — 参数组装与分发

```java
// ChatServiceImpl.java
@Override
public void streamChat(String prompt, String conversationId, SseEmitter emitter) {
    routingChatService.streamChat(prompt, conversationId, null, null, emitter);
}

@Override
public void streamChatWithRag(String prompt, String conversationId,
                               String name, String major, SseEmitter emitter) {
    PromptTemplate promptTemplate = new PromptTemplate(SystemConstants.SYSTEM_MASTER_PROMPT);
    HashMap<String, Object> map = new HashMap<>();
    map.put("name", name);
    map.put("major", major);
    String systemPrompt = promptTemplate.render(map);          // 渲染系统提示词
    routingChatService.streamChat(prompt, conversationId,
                                  systemPrompt, vectorStore, emitter);
}
```

**关键点**：
- 普通流式：`systemPrompt = null`，`vectorStore = null`，下层跳过对应逻辑
- RAG 流式：将 `name`/`major` 填入模板渲染出系统提示词，同时传入 `vectorStore`，下层追加 `QuestionAnswerAdvisor`

---

### 3.3 RoutingChatService — 路由主循环

```java
public void streamChat(String prompt, String conversationId,
                       String systemPrompt, VectorStore vectorStore,
                       SseEmitter emitter) {
    int total = factory.size();                      // 候选总数快照
    for (int i = 0; i < total; i++) {
        ChatModelEntry entry = factory.poll();       // 从队头取出候选
        if (entry == null) break;

        if (!circuitBreaker.allowCall(entry.id())) { // 熔断检查
            factory.offerTail(entry);                // 跳过，放回队尾
            continue;
        }

        if (tryStreamWithEntry(..., emitter)) {
            return;  // 探测成功，emitter 已接管，退出循环
        }
        factory.offerTail(entry);  // 探测失败，放回队尾，继续下一个
    }

    // 全部候选均失败
    emitter.send(SseEmitter.event().name("error").data("所有 Chat 候选均失败，请稍后重试"));
    emitter.complete();
}
```

**关键点**：
- `factory.size()` 在循环前获取快照，避免因 `offerTail` 导致无限循环
- `factory.poll()` / `factory.offerTail()` 都加了 `synchronized`，线程安全
- 每次迭代取出一个候选，探测失败后放回队尾（**不丢弃**），下次请求可重试

---

### 3.4 首包探测机制 — tryStreamWithEntry

这是 SSE 流式的核心保护逻辑，目的是**在用户看到任何输出之前**确认模型是否正常工作：

```java
private boolean tryStreamWithEntry(ChatModelEntry entry, ..., SseEmitter emitter) {
    CompletableFuture<Boolean> probeFuture = new CompletableFuture<>();
    AtomicBoolean probeCompleted = new AtomicBoolean(false);  // CAS 保证只触发一次

    Flux<String> flux = buildSpec(entry, ...).stream().content();

    Disposable[] sub = new Disposable[1];
    sub[0] = flux.subscribe(
        chunk -> {
            // ① 首个 chunk 到达：探测成功
            if (probeCompleted.compareAndSet(false, true)) {
                circuitBreaker.markSuccess(entry.id());
                factory.offerTail(entry);
                probeFuture.complete(true);    // 通知主线程可以放行
            }
            emitter.send(SseEmitter.event().data(chunk)); // 推送给客户端
        },
        error -> {
            // ② 出现异常
            circuitBreaker.markFailure(entry.id());
            if (probeCompleted.compareAndSet(false, true)) {
                probeFuture.complete(false);   // 通知主线程探测失败
            } else {
                // 探测已成功但流中途断了，通知客户端
                emitter.send(SseEmitter.event().name("error").data("[流中断]"));
                emitter.completeWithError(error);
            }
        },
        () -> {
            // ③ 流结束
            if (probeCompleted.compareAndSet(false, true)) {
                probeFuture.complete(false);   // 空流视为失败
            } else {
                emitter.complete();            // 正常结束
            }
        }
    );

    // 主线程阻塞等待首包，最多 10 秒
    boolean success = probeFuture.get(10_000, TimeUnit.MILLISECONDS);
    if (!success) sub[0].dispose();  // 失败则取消订阅
    return success;
}
```

**三种结果及处理**：

| 情形 | 触发条件 | 处理方式 |
|------|----------|----------|
| **探测成功** | 首个 chunk 正常到达 | `markSuccess` + `probeFuture.complete(true)`，后续 chunk 继续 `emitter.send()` |
| **探测失败** | 报错或空流 | `markFailure` + `probeFuture.complete(false)`，取消订阅，切换下一候选 |
| **首包超时** | 10 秒内无任何 chunk | `markFailure` + 取消订阅，切换下一候选 |

---

### 3.5 buildSpec — 请求规格构建

```java
private ChatClient.ChatClientRequestSpec buildSpec(ChatModelEntry entry,
                                                    String prompt, String conversationId,
                                                    String systemPrompt, VectorStore vectorStore) {
    ChatOptions options = entry.buildOptions();  // 动态覆盖模型名

    var spec = ChatClient.builder(entry.delegate())  // 用候选的底层 ChatModel 构建客户端
            .build()
            .prompt()
            .options(options)          // 注入模型名（DashScopeChatOptions / OpenAiChatOptions）
            .user(prompt)
            .advisors(MessageChatMemoryAdvisor.builder(chatMemory)
                      .conversationId(conversationId).build());  // 记忆 Advisor

    if (systemPrompt != null && !systemPrompt.isBlank()) {
        spec = spec.system(systemPrompt);       // RAG 场景注入系统提示词
    }
    if (vectorStore != null) {
        spec = spec.advisors(new QuestionAnswerAdvisor(vectorStore)); // RAG 检索 Advisor
    }
    return spec;
}
```

**Advisor 执行顺序**（Spring AI 内部保证）：
```
请求进入 → MessageChatMemoryAdvisor (加载历史)
         → [QuestionAnswerAdvisor (向量检索，仅 RAG)]
         → 调用 ChatModel
         → MessageChatMemoryAdvisor (保存新消息)
         → 响应返回
```

---

### 3.6 ChatCircuitBreaker — 三态熔断

每个候选模型独立维护一套熔断状态，互不影响：

```
正常调用                 连续失败 >= failureThreshold(2)
  CLOSED ─────────────────────────────────────────→ OPEN
    ↑                                                  │
    │  markSuccess()          超过 openDurationMs(30s) │
    │                                                  ↓
    └──────────────────── HALF_OPEN ←──────────────────┘
           探测成功↗         ↘ 探测失败（重回 OPEN）
```

| 方法 | 触发时机 | 效果 |
|------|----------|------|
| `allowCall(id)` | 每次路由前 | CLOSED/HALF_OPEN(无探测在途) → true；OPEN/HALF_OPEN(有探测在途) → false |
| `markSuccess(id)` | 首包探测成功后 | 状态→CLOSED，failureCount 清零 |
| `markFailure(id)` | 探测失败/超时/异常 | failureCount++，达阈值→OPEN，记录开始时间 |

---

## 四、数据格式

### 客户端收到的 SSE 事件

正常 chunk（每个 token 一条事件）：
```
data: 你好
data: ，我
data: 是
data: 三条
```

流中断事件：
```
event: error
data: [流中断]
```

全部候选失败事件：
```
event: error
data: 所有 Chat 候选均失败，请稍后重试
```

---

## 五、接口一览

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/chat/stream` | 纯对话流式，参数：`prompt`、`conversationId`（可选） |
| GET | `/api/chat/stream/withRag` | RAG 增强流式，额外参数：`name`（默认三条）、`major`（默认金融等） |

**curl 验证示例**：
```bash
# 纯对话
curl -N "http://localhost:8123/api/chat/stream?prompt=你好&conversationId=test1"

# RAG 增强
curl -N "http://localhost:8123/api/chat/stream/withRag?prompt=介绍一下自己&name=小智&major=计算机科学"
```

---

## 六、配置参考

```yaml
chat:
  selection:
    failure-threshold: 2      # 连续失败 2 次触发熔断
    open-duration-ms: 30000   # 熔断持续 30 秒后进入 HALF_OPEN
  candidates:
    - id: dashscope-qwen      # 主候选
      provider: dashscope
      model: qwen-plus
      priority: 1
      enabled: true
    - id: siliconflow-deepseek  # 备用候选
      provider: openai
      model: deepseek-ai/DeepSeek-V3
      priority: 2
      enabled: true
```

---

## 七、关键类索引

| 类 | 包路径 | 职责 |
|----|--------|------|
| `ChatController` | `controller/` | HTTP 入口，创建并返回 `SseEmitter` |
| `ChatServiceImpl` | `service/impl/` | 参数组装，分发到路由层 |
| `RoutingChatService` | `chat/` | 路由主循环，首包探测逻辑 |
| `ChatModelFactory` | `chat/` | 候选队列管理（Deque + poll/offerTail） |
| `ChatCircuitBreaker` | `chat/` | 三态熔断状态机 |
| `ChatModelProperties` | `chat/` | 绑定 `chat.*` 配置 |
| `ChatModelCandidate` | `chat/` | 候选配置 POJO |
