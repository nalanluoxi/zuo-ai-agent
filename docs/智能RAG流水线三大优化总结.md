# 智能 RAG 流水线三大优化总结

> 完成日期：2026-05-16

---

## 一、背景与目标

### 改造前的问题

原有的 `streamChatWithRagPipeline` 是一条"平铺式"流水线：不论用户问什么，都会经历 **改写 → 全局向量检索 → 重排序 → LLM 回复** 四步，存在以下三个明显短板：

| 问题 | 具体表现 |
|------|---------|
| 无意图感知 | 系统不知道用户问的是哪个领域，无法做针对性检索 |
| 闲聊浪费资源 | "你好""你是谁"等问候语也会触发向量检索，增加延迟与成本 |
| 单通道检索精度不足 | 只做全局向量检索，缺乏对特定知识库的定向召回 |

### 优化目标

| 优化项 | 实现方式 |
|--------|---------|
| 用户意图检测 + 意图树管理 | 数据库表 `t_intent_node` + LLM 分类器 |
| 闲聊/系统问题短路 | 命中 `is_system=1` 节点时跳过向量检索，直接 LLM 回复 |
| 多通道并行检索 | 全局通道 + 意图定向通道并行执行，结果合并去重 |

---

## 二、整体架构

### 新增端点

```
GET /api/chat/stream/smart
```

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `prompt` | String | 必填 | 用户原始问题 |
| `conversationId` | String | 自动生成 | 会话 ID |
| `name` | String | "三条" | AI 助手名称 |
| `major` | String | "金融、宪法学、投资股价学" | 专业领域 |
| `enableRewrite` | boolean | true | 是否启用问题改写 |
| `enableRerank` | boolean | true | 是否启用重排序 |

### 完整流水线执行图

```
用户原始问题 (prompt)
  │
  ▼ ① 问题改写（enableRewrite=true）
  QueryRewriter.rewrite()
  │    去除指代词，改写为自包含检索语句
  │    失败时静默降级，使用原始问题
  │
  ▼ ② 意图分类
  IntentClassifier.classify()
  │    LLM 对照意图树文本输出节点 ID
  │    失败时降级为 IntentResult.unknown()
  │
  ▼ ③ 意图路由判断
  ├─── isSystem=true（闲聊/系统节点）
  │         │
  │         ▼ 短路：直接 LLM 流式推送（不走向量检索）
  │         RoutingChatService.streamChat()
  │         return ← 流水线结束
  │
  └─── 非系统节点
            │
            ▼ ④ 多通道并行检索
            MultiChannelRetriever.retrieve()
            │    ┌─── 全局通道：全量向量空间检索（top-6）
            │    └─── 意图定向通道：按 kb_id 过滤检索（top-6）
            │         （confidence < 0.5 时，kbId=null，只走全局通道）
            │    两路结果合并去重，意图定向结果在前
            │
            ▼ ⑤ 重排序（enableRerank=true）
            DocumentReranker.rerank()
            │    LLM 对每个文档评分（0-10），取 top-3
            │
            ▼ ⑥ 拼装系统提示词
            buildBaseSystemPrompt() + 【参考知识库内容】
            │
            ▼ ⑦ 路由流式推送
            RoutingChatService.streamChat()
                 多候选模型路由 + 熔断保护 + SSE 推送
```

---

## 三、优化一：意图检测与意图树管理

### 3.1 数据库表设计

```sql
-- 执行 src/main/resources/sql/intent.sql
CREATE TABLE IF NOT EXISTS t_intent_node (
    id          BIGINT PRIMARY KEY,
    parent_id   BIGINT,                          -- NULL 表示顶级节点
    label       VARCHAR(64)  NOT NULL,           -- 节点名称
    description VARCHAR(256),                    -- 节点描述（用于 LLM 分类 Prompt）
    level       SMALLINT     NOT NULL DEFAULT 1, -- 1=domain, 2=category, 3=topic
    is_system   SMALLINT     NOT NULL DEFAULT 0, -- 1=系统/闲聊节点
    kb_id       BIGINT,                          -- 关联知识库（topic 级别才填）
    sort_order  INT          NOT NULL DEFAULT 0,
    create_time TIMESTAMP    NOT NULL DEFAULT NOW(),
    update_time TIMESTAMP    NOT NULL DEFAULT NOW(),
    deleted     SMALLINT     NOT NULL DEFAULT 0
);
```

**三级层次结构：**

```
level=1 (domain)    金融
level=2 (category)    ├── 金融工程
                      └── 投资估价
level=1 (domain)    宪法学
level=2 (category)    └── 宪法基础
level=1 (domain)    闲聊/通用  ← is_system=1，命中即短路
```

**初始化数据（intent.sql 内置）：**

| ID | label | level | is_system | kb_id |
|----|-------|-------|-----------|-------|
| 1 | 闲聊/通用 | 1 | 1 | null |
| 2 | 金融 | 1 | 0 | null |
| 3 | 金融工程 | 2 | 0 | null |
| 4 | 投资估价 | 2 | 0 | null |
| 5 | 宪法学 | 1 | 0 | null |
| 6 | 宪法基础 | 2 | 0 | null |

> **注意**：`kb_id` 需在数据库中更新为实际知识库 ID，意图定向检索才能生效。

### 3.2 意图树缓存服务（`IntentTreeService`）

**职责：**
- 启动时从 `t_intent_node` 加载全量节点到内存 `Map<Long, IntentNodeDO>`
- 定时刷新缓存（由 `intent.cache.refresh-interval-ms` 配置，默认 5 分钟）
- `buildTreeText()`：将节点树格式化为纯文本，用于 LLM 分类 Prompt

**`buildTreeText()` 输出示例：**

```
[ID=1] 闲聊/通用 (系统节点): 用户问候、闲聊、系统类问题...
[ID=2] 金融: 金融工程、金融产品、金融市场相关问题
  [ID=3] 金融 > 金融工程: 金融工程技术、衍生品定价...
  [ID=4] 金融 > 投资估价: 股票估值、市盈率...
[ID=5] 宪法学: 宪法条文、公民权利...
  [ID=6] 宪法学 > 宪法基础: 宪法基本原则...
```

**缓存刷新配置：**

```yaml
# application.yaml
intent:
  cache:
    refresh-interval-ms: 300000   # 5分钟，单位毫秒
```

### 3.3 意图分类器（`IntentClassifier`）

**分类 Prompt（`SystemConstants.INTENT_CLASSIFY_PROMPT`）：**

```
你是一个意图分类助手。请根据以下意图树，判断用户的问题属于哪个节点。
意图树：
{intentTree}

用户问题：{query}

要求：
1. 只输出对应节点的 id（整数），不要任何解释
2. 如果是闲聊、问候、系统问题（如"你好"、"你是谁"），输出节点 id = -1
3. 如果不确定，输出 -1
```

**分类结果 `IntentResult`：**

| 字段 | 类型 | 说明 |
|------|------|------|
| `intentNodeId` | Long | 匹配节点 ID；-1 表示未知/闲聊 |
| `label` | String | 节点名称 |
| `confidence` | double | 置信度（0.0~1.0）；LLM 返回有效 ID 时为 0.85 |
| `isSystem` | boolean | 是否系统节点（true=短路） |
| `kbId` | Long | 关联知识库 ID（可为 null） |

**降级策略：**

```
LLM 返回非整数      → IntentResult.unknown()（confidence=0，走全局检索）
LLM 返回未知节点 ID → IntentResult.unknown()
LLM 调用异常        → IntentResult.unknown()
意图树为空          → IntentResult.unknown()
LLM 返回 -1         → IntentResult.system(-1, "闲聊/通用")（触发短路）
```

### 3.4 意图节点管理接口（`IntentNodeController`）

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/intent/node` | 创建意图节点 |
| PUT | `/intent/node/{id}` | 更新意图节点 |
| DELETE | `/intent/node/{id}` | 删除意图节点（逻辑删除） |
| GET | `/intent/nodes` | 查询全量节点（按层级排序） |
| POST | `/intent/cache/refresh` | 手动刷新内存缓存 |

> **动态调整意图树**：通过 CRUD 接口修改节点后，调用 `/intent/cache/refresh` 使变更立即生效，无需重启服务。

---

## 四、优化二：闲聊/系统问题短路

### 4.1 短路判断逻辑

在 `SmartRagPipeline.execute()` 的第③步：

```java
// 命中系统节点 → 短路，不走向量检索
if (intentResult.isSystem()) {
    log.info("[SmartRagPipeline] conId={} 命中系统节点，短路，直接 LLM 回复", conId);
    String sysPrompt = buildBaseSystemPrompt(ctx);
    routingChatService.streamChat(ctx.getOriginalPrompt(), conId, sysPrompt, null, emitter);
    return;  // 流水线结束，后续检索步骤不执行
}
```

**触发条件（满足任一）：**
1. 意图分类结果 `isSystem=true`（命中 `t_intent_node.is_system=1` 的节点）
2. LLM 返回 `-1`（显式表示闲聊/不确定）

### 4.2 短路与完整流水线对比

| 维度 | 短路路径 | 完整路径 |
|------|---------|---------|
| 触发场景 | 闲聊、问候、系统类问题 | 知识库领域问题 |
| 向量检索 | **不执行** | 执行（双通道并行） |
| 重排序 | **不执行** | 执行（LLM 评分） |
| 额外 LLM 调用 | 0次（仅分类1次） | 1次分类 + N次重排序 |
| 响应延迟 | 低（仅分类耗时） | 较高（检索 + 重排序） |
| 知识库上下文 | 无 | 注入 top-3 相关文档 |

### 4.3 验证方法

启动服务后，发送：
```
GET /api/chat/stream/smart?prompt=你好
```

查看日志，应出现：
```
[SmartRagPipeline] conId=xxx 命中系统节点，短路，直接 LLM 回复
```

**不应出现** `MultiChannelRetriever` 相关日志，说明向量检索被成功跳过。

---

## 五、优化三：多通道并行检索

### 5.1 双通道设计

```
查询词 (searchQuery)
    │
    ├──────────────────────────────────────────┐
    │                                          │
    ▼                                          ▼
[全局通道] retrievalExecutor                [意图定向通道] retrievalExecutor
VectorStore.similaritySearch()              VectorStore.similaritySearch()
query=searchQuery                           query=searchQuery
topK=6                                      topK=6
（全量向量空间，无过滤）                     filterExpression: kb_id == {kbId}
                                            （仅在关联知识库中检索）
    │                                          │
    └──────────────┬───────────────────────────┘
                   │ CompletableFuture.allOf()
                   │ 超时：10秒
                   ▼
              合并去重（意图定向结果优先）
              │  意图定向结果放前，全局结果补充
              │  按 document.id 去重
                   ▼
              merged docs（传入重排序）
```

### 5.2 意图定向通道触发条件

| 条件 | kbId 取值 | 行为 |
|------|----------|------|
| `intentResult.confidence >= 0.5` 且 `kbId != null` | 节点配置的 kbId | 双通道并行 |
| `intentResult.confidence < 0.5` | null | 仅全局通道 |
| 意图为 unknown（降级） | null | 仅全局通道 |
| 节点未配置 `kb_id` | null | 仅全局通道 |

### 5.3 并行线程池（`retrievalExecutor`）

在 `AsyncConfig` 新增专用线程池：

```java
@Bean("retrievalExecutor")
public Executor retrievalExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(4);    // 每次请求最多 2 个通道并行，预留余量
    executor.setMaxPoolSize(16);    // 高并发时扩展
    executor.setQueueCapacity(200);
    executor.setThreadNamePrefix("retrieval-");
    executor.setRejectedExecutionHandler(new CallerRunsPolicy()); // 降级同步执行
    executor.initialize();
    return executor;
}
```

> 与文档入库的 `ingestionExecutor` 隔离，互不影响。

### 5.4 故障降级

```
并行检索异常或超时（10秒）
  │
  └─── 降级：取消两个 Future，同步执行全局检索
       log.warn("[MultiChannelRetriever] 并行检索超时或异常，降级为全局检索")
```

---

## 六、核心组件说明

### 6.1 新增文件清单

| 文件路径 | 类型 | 职责 |
|---------|------|------|
| `intent/entity/IntentNodeDO.java` | 实体 | 意图节点数据库映射 |
| `intent/mapper/IntentNodeMapper.java` | Mapper | MyBatis Plus CRUD |
| `intent/model/IntentResult.java` | VO | 分类结果（nodeId/label/confidence/isSystem/kbId） |
| `intent/service/IntentTreeService.java` | Service | 节点加载、内存缓存、定时刷新、树文本生成 |
| `intent/service/IntentClassifier.java` | Component | LLM 意图分类，含降级策略 |
| `rag/MultiChannelRetriever.java` | Component | 双通道并行检索，合并去重 |
| `pipeline/RagPipelineContext.java` | VO | 流水线上下文，各阶段共享数据 |
| `pipeline/SmartRagPipeline.java` | Component | 完整 7 步智能流水线入口 |
| `controller/IntentNodeController.java` | Controller | 意图节点 CRUD + 缓存刷新 |
| `resources/sql/intent.sql` | DDL | 建表 + 初始化数据 |

### 6.2 修改文件清单

| 文件路径 | 修改内容 |
|---------|---------|
| `service/ChatService.java` | 新增 `streamChatSmart()` 方法签名 |
| `service/impl/ChatServiceImpl.java` | 实现 `streamChatSmart()`，注入并委托 `SmartRagPipeline` |
| `controller/ChatController.java` | 新增 `GET /chat/stream/smart` 端点 |
| `constants/SystemConstants.java` | 新增 `INTENT_CLASSIFY_PROMPT` 常量 |
| `config/AsyncConfig.java` | 新增 `retrievalExecutor` Bean |
| `resources/application.yaml` | 新增 `intent.cache.refresh-interval-ms` 配置 |

---

## 七、接口全览

### 对话接口对比

| 接口 | 路径 | 意图检测 | 向量检索 | 重排序 | 适用场景 |
|------|------|---------|---------|--------|---------|
| 基础流式对话 | `GET /chat/stream` | ✗ | ✗ | ✗ | 纯 LLM 对话 |
| RAG 流式对话 | `GET /chat/stream/withRag` | ✗ | 单通道（QuestionAnswerAdvisor） | ✗ | 简单直接问题 |
| RAG 流水线 | `GET /chat/stream/rag/pipeline` | ✗ | 单通道（全局） | ✓ | 含指代词的问题 |
| **智能流水线** | **`GET /chat/stream/smart`** | **✓** | **双通道并行** | **✓** | **全场景（推荐）** |

### 意图管理接口

| 方法 | 路径 | 功能 |
|------|------|------|
| GET | `/intent/nodes` | 查询全量意图节点 |
| POST | `/intent/node` | 创建节点 |
| PUT | `/intent/node/{id}` | 更新节点 |
| DELETE | `/intent/node/{id}` | 逻辑删除节点 |
| POST | `/intent/cache/refresh` | 手动刷新缓存 |

---

## 八、数据库初始化

在已有 `knowledge.sql` 和 `chat-memory.sql` 基础上，新增执行 `intent.sql`：

```
建表执行顺序：
  1. knowledge.sql      — 知识库表、文档表、文件存储表（已有）
  2. chat-memory.sql    — 对话记忆表（已有）
  3. intent.sql         — 意图节点表 + 初始数据（本次新增）
```

初始化后数据库中将有 6 条预置意图节点（闲聊 + 金融领域 + 宪法学领域）。

**将意图节点关联到知识库：**

```sql
-- 查询现有知识库
SELECT id, name FROM t_knowledge_base WHERE deleted = 0;

-- 将"宪法基础"节点关联到宪法学知识库（假设知识库 ID 为 123）
UPDATE t_intent_node SET kb_id = 123 WHERE id = 6;

-- 将"投资估价"节点关联到投资知识库
UPDATE t_intent_node SET kb_id = 456 WHERE id = 4;
```

---

## 九、配置速查

```yaml
# 意图树缓存
intent:
  cache:
    refresh-interval-ms: 300000   # 5分钟，单位毫秒

# 并行检索线程池（AsyncConfig.java，非 yaml 配置）
# retrievalExecutor: core=4, max=16, queue=200
```

---

## 十、验证步骤

### 步骤 1：编译验证

```bash
cd zuo-ai-agent
mvn clean package -DskipTests
# 预期：BUILD SUCCESS
```

### 步骤 2：数据库初始化

在 Supabase SQL Editor 中执行：
```sql
-- 执行 src/main/resources/sql/intent.sql
```

### 步骤 3：启动服务

```bash
mvn spring-boot:run
# 启动日志应出现：
# [IntentTreeService] 意图缓存刷新完成，共 6 个节点
# 并行检索线程池初始化完成: core=4, max=16, queue=200
```

### 步骤 4：测试闲聊短路

```bash
GET http://localhost:8123/api/chat/stream/smart?prompt=你好
```

**预期**：
- 日志出现 `命中系统节点，短路，直接 LLM 回复`
- 日志**不出现** `MultiChannelRetriever` 相关内容
- 响应速度明显快于 RAG 流水线

### 步骤 5：测试知识库问答

```bash
GET http://localhost:8123/api/chat/stream/smart?prompt=什么是股票市盈率
```

**预期**：
- 日志出现 `[IntentClassifier] 分类结果: IntentResult{nodeId=4, label='投资估价'...}`
- 日志出现 `[MultiChannelRetriever] 全局:X 定向:Y 合并:Z`
- 日志出现 `重排序后保留 3 个文档片段`

### 步骤 6：测试意图树动态调整

1. 调用 `POST /intent/node` 新增一个节点
2. 调用 `POST /intent/cache/refresh` 刷新缓存
3. 再次发起对话，验证新节点能被分类命中

### 步骤 7：Knife4j UI 验证

访问 `http://localhost:8123/api/doc.html`，确认以下接口出现在文档中：
- `GET /chat/stream/smart`
- `GET /intent/nodes`
- `POST /intent/node`
- `POST /intent/cache/refresh`

---

## 十一、常见问题

**Q：意图分类结果总是 unknown，双通道没有走意图定向通道？**

A：检查以下几点：
1. `t_intent_node` 表是否有数据（`GET /intent/nodes` 确认）
2. 对应节点的 `kb_id` 是否已填写（`kb_id=null` 时不走定向通道）
3. LLM 分类 Prompt 中的意图树文本是否正确（日志级别调为 DEBUG 查看）

**Q：如何添加新的业务领域？**

A：
1. 调用 `POST /intent/node` 创建 domain 节点（level=1）
2. 可选创建 category 子节点（level=2，parentId 填 domain ID）
3. 填写 `kb_id` 关联到对应知识库
4. 调用 `POST /intent/cache/refresh` 使变更生效

**Q：短路后的闲聊回复质量如何保证？**

A：短路路径仍会使用 `SYSTEM_MASTER_PROMPT`（含助手名称和专业领域定义）+ 对话历史记忆，LLM 会以设定的角色身份进行回复，只是不注入知识库上下文。

**Q：并行检索超时了怎么办？**

A：`MultiChannelRetriever` 内置 10 秒超时兜底，超时后自动取消两个 Future，降级为同步执行全局检索，日志会打印 `warn` 提示。
