# RAG 流水线 + 对话记忆改造总结

> 完成日期：2026-05-15

---

## 一、背景与目标

### 改造前的问题

1. **RAG 对话质量偏低**：检索直接用用户原始问题，含代词或上下文依赖的问句（"它是什么""上面说的那个方法"）向量检索效果差；召回文档仅按余弦相似度排序，未做语义相关性二次筛选。
2. **对话历史不持久**：`MapBasedChatMemory` 将消息存在内存中，服务重启后历史丢失；长对话无摘要压缩，上下文窗口无限增长。
3. **ingestion 参数硬编码**：分块大小、关键词数量等写死在代码中，无法通过配置调整；`MyDocumentEnricher` 未集成到入库流水线，关键词/摘要增强实际上没有生效。

### 改造目标

| 目标 | 实现方式 |
|------|---------|
| 提升 RAG 检索质量 | 问题改写 + 重排序流水线 |
| 持久化对话历史 | DB 持久化 + 自动摘要压缩 |
| ingestion 配置化 + 增强节点上线 | 配置属性类 + enricher 集成 |
| 分块策略文档化 | 对比文档 |

---

## 二、改造内容详解

### 2.1 完整 RAG 对话流水线

#### 新增端点

```
GET /api/chat/stream/rag/pipeline
```

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `prompt` | String | 必填 | 用户问题 |
| `conversationId` | String | 自动生成 | 会话 ID |
| `name` | String | "三条" | AI 助手名称 |
| `major` | String | "金融、宪法学、投资股价学" | 专业领域 |
| `enableRewrite` | boolean | true | 是否启用问题改写 |
| `enableRerank` | boolean | true | 是否启用重排序 |

#### 流水线执行步骤

```
用户原始问题
  │
  ▼（enableRewrite=true）
QueryRewriter.rewrite()          ← 去除指代词，改写为自包含的检索语句
  │
  ▼
VectorStore.similaritySearch()   ← top-6 向量召回（保留足够候选供重排序）
  │
  ▼（enableRerank=true）
DocumentReranker.rerank()        ← LLM 逐文档评分（0-10），取 top-3
  │
  ▼
拼装系统提示词 + 知识库上下文
  │
  ▼
RoutingChatService.streamChat()  ← 路由多候选模型，SSE 流式推送
```

#### 核心类

**`QueryRewriter`**（`rag/QueryRewriter.java`）

- 向 LLM 发送改写请求，Prompt 要求：去除人称指代、展开为完整自包含句子、只输出改写结果
- 失败时静默降级，返回原始 query，不中断主流程

**`DocumentReranker`**（`rag/DocumentReranker.java`）

- 对每个召回文档单独评分，Prompt 要求：0-10 分，只输出整数
- 文档内容超过 800 字符时截断，防止 prompt 过长
- 按分数降序取 `topK`（默认 3）
- 评分异常的文档赋默认分 0，排在末尾

---

### 2.2 DB 持久化对话历史 + 摘要压缩

#### 新增数据库表

```sql
-- 执行 src/main/resources/sql/chat-memory.sql
CREATE TABLE IF NOT EXISTS t_chat_memory (
    id              BIGSERIAL    PRIMARY KEY,
    conversation_id VARCHAR(64)  NOT NULL,
    role            VARCHAR(16)  NOT NULL,   -- user / assistant / system
    content         TEXT         NOT NULL,
    create_time     TIMESTAMP    NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_chat_memory_conversation ON t_chat_memory (conversation_id, id ASC);
```

#### 核心类 `DbBasedChatMemory`（`chatmemory/DbBasedChatMemory.java`）

实现 Spring AI `ChatMemory` 接口，替换原有的 `MapBasedChatMemory`。

**摘要压缩触发逻辑：**

```
每次 add() 写入后统计该会话消息总数
  │
  ├─ 总数 ≤ summaryStartTurns * 2 → 不压缩
  │
  └─ 总数 > summaryStartTurns * 2 → 触发压缩
       ├─ 取最旧的一半消息（按 id 升序）
       ├─ 调用 LLM 生成摘要（不超过200字）
       ├─ DELETE 旧消息
       └─ INSERT 摘要（role=system，content 以 [对话摘要] 开头）
```

**可配置参数：**

```yaml
chat:
  memory:
    summary-start-turns: 10   # 超过 10 轮（20条消息）触发压缩
    history-keep: 20          # get() 默认返回最近 20 条
```

#### `ChatClientConfig` 变更

```java
// 之前
@Bean
public ChatMemory chatMemory() {
    return new MapBasedChatMemory();  // 内存，重启丢失
}

// 之后
@Bean
public ChatMemory chatMemory(JdbcTemplate jdbcTemplate, ChatModel chatModel) {
    return new DbBasedChatMemory(jdbcTemplate, chatModel, summaryStartTurns, historyKeep);
}
```

> **切换回内存模式**：将 `ChatClientConfig` 中的 Bean 改为 `return new MapBasedChatMemory()` 即可（开发/测试时避免依赖数据库）。

---

### 2.3 ingestion 流水线配置化 + 增强节点上线

#### 配置项（`application.yaml`）

```yaml
ingestion:
  chunking:
    chunk-size: 512      # 默认分块大小（字符数）
    overlap-size: 128    # 相邻块重叠大小（字符数）
  enrichment:
    enable-keyword: true   # 开启关键词增强（默认开启）
    enable-summary: false  # 开启摘要增强（默认关闭，耗时较高）
    keyword-count: 5       # 提取关键词数量
```

#### `IngestionProperties`（新增）

绑定上述配置，注入到 `DocumentIngestionService`，替换原有硬编码常量。

#### `DocumentIngestionService` 流水线变更

```
原来：
  解析 → 分块（hardcode 512/128）→ 写向量库

现在：
  解析 → 分块（从配置读取）→ 元数据增强（关键词/摘要，按配置开关）→ 写向量库
```

增强步骤：
1. `enableKeyword=true` → 调用 `MyDocumentEnricher.enrichDocumentsByKeyword(docs, keywordCount)`，关键词写入 metadata `excerpt_keywords`
2. `enableSummary=true` → 调用 `MyDocumentEnricher.enrichDocumentsBySummary(docs)`，写入 metadata `section_summary`（PREVIOUS/CURRENT/NEXT 三种）
3. 增强失败时记录 warn 日志，跳过增强步骤，不中断入库

#### `MyDocumentEnricher` 变更

- 访问修饰符 `class` → `public class`，方法 → `public`（供 ingestion 包调用）
- 新增重载：`enrichDocumentsByKeyword(List<Document>, int keywordCount)`，支持传入自定义关键词数量

---

### 2.4 分块策略文档

新增 `src/main/resources/docs/chunking-strategy.md`，内容包括：

- 三种策略对比表（FixedSize / StructureAware / Token）
- 各参数含义与默认值
- 边界对齐优先级、URL感知、CJK软换行修复说明
- 按文档类型的策略选择建议
- 元数据增强效果与代价对比
- ragent vs zuo-ai-agent 差异汇总

---

## 三、文件变更清单

### 新增文件

| 文件路径 | 说明 |
|---------|------|
| `rag/QueryRewriter.java` | 查询改写器（LLM 去除指代词） |
| `rag/DocumentReranker.java` | 文档重排序器（LLM 评分 0-10） |
| `chatmemory/DbBasedChatMemory.java` | DB 持久化对话记忆 + 摘要压缩 |
| `knowledge/ingestion/IngestionProperties.java` | ingestion 流水线配置属性类 |
| `resources/sql/chat-memory.sql` | t_chat_memory 建表脚本 |
| `resources/docs/chunking-strategy.md` | 分块策略对比文档 |

### 修改文件

| 文件路径 | 修改内容 |
|---------|---------|
| `service/ChatService.java` | 新增 `streamChatWithRagPipeline` 接口方法 |
| `service/impl/ChatServiceImpl.java` | 实现 RAG 流水线方法，注入 QueryRewriter/DocumentReranker |
| `controller/ChatController.java` | 新增 `/stream/rag/pipeline` 端点 |
| `config/ChatClientConfig.java` | ChatMemory Bean 切换为 DbBasedChatMemory |
| `rag/MyDocumentEnricher.java` | 改为 public，新增 keywordCount 重载方法 |
| `knowledge/ingestion/DocumentIngestionService.java` | 注入 enricher 和配置，集成增强步骤，分块参数配置化 |
| `resources/application.yaml` | 新增 `chat.memory.*`、`ingestion.*` 配置块 |

---

## 四、数据库初始化补充

在已有 `knowledge.sql` 基础上，额外执行 `chat-memory.sql`：

```sql
-- Supabase SQL Editor 中执行
CREATE TABLE IF NOT EXISTS t_chat_memory (
    id              BIGSERIAL    PRIMARY KEY,
    conversation_id VARCHAR(64)  NOT NULL,
    role            VARCHAR(16)  NOT NULL,
    content         TEXT         NOT NULL,
    create_time     TIMESTAMP    NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_chat_memory_conversation ON t_chat_memory (conversation_id, id ASC);
```

完整建表顺序：
1. `knowledge.sql`（知识库表、文档表、文件表）— 已有
2. `chat-memory.sql`（对话记忆表）— 本次新增

---

## 五、接口对比

| 场景 | 旧接口 | 新接口 |
|------|--------|--------|
| 流式对话（无RAG） | `GET /api/chat/stream` | 不变 |
| 流式对话 + RAG（QuestionAnswerAdvisor） | `GET /api/chat/stream/withRag` | 不变（保留兼容） |
| 流式对话 + 完整RAG流水线 | — | `GET /api/chat/stream/rag/pipeline`（新增） |

**新旧 RAG 接口差异：**

| 维度 | `/stream/withRag` | `/stream/rag/pipeline` |
|------|------------------|----------------------|
| 检索方式 | QuestionAnswerAdvisor（直接用原始问题） | 问题改写 → 向量检索 → 重排序 |
| 文档数量 | top-4（默认） | top-6 召回，重排序取 top-3 |
| 延迟 | 较低 | 改写+重排序额外 2~4 次 LLM 调用，延迟更高 |
| 适用场景 | 简单直接的问题 | 含指代/歧义的对话式问题 |

---

## 六、配置速查

```yaml
# 对话记忆
chat:
  memory:
    summary-start-turns: 10   # 触发摘要压缩的轮数阈值
    history-keep: 20           # 历史记忆保留最近 N 条消息

# 文档入库流水线
ingestion:
  chunking:
    chunk-size: 512
    overlap-size: 128
  enrichment:
    enable-keyword: true      # 关键词增强（每块 1 次 LLM 调用）
    enable-summary: false     # 摘要增强（每块 1-3 次 LLM 调用，默认关闭）
    keyword-count: 5
```