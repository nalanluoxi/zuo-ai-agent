# zuo-ai-agent 文档分块 + Embedding 熔断机制改造总结

> 完成日期：2026-05-08
> 最后更新：2026-05-11（补全熔断细节、维度变更、容错机制、类索引）

---

## 一、背景与目标

### 改造前的问题

`zuo-ai-agent` 原有文档上传接口只做了"文件写入数据库 + 插入文档记录"两步，缺少后续的向量化入库逻辑，导致：

- 文档 `status` 字段永远停留在 `pending`，无法检索
- `PgVector` 向量表中没有任何数据
- `VectorStoreConfig.java` 存在语法错误（第 42 行缺少 `}`），项目无法正常启动

### 改造目标

在文件上传成功后，**自动**完成以下完整入库链路：

```
解析文本 → 文本分块 → Embedding 向量化（含多候选熔断） → 写入 PgVector
```

---

## 二、整体架构图

```
POST /{kbId}/docs/upload
        │
        ▼
KnowledgeDocumentController.upload()
        │
        ▼
KnowledgeDocumentServiceImpl.upload()
  ├─ 文件字节写入 t_knowledge_document_file
  ├─ 插入 t_knowledge_document（status = pending）
  └─ @Async 触发 ──────────────────────────────────┐
                                                   │
                    ┌──────────────────────────────▼──────────────────────────────┐
                    │            DocumentIngestionService.ingest(docId)            │
                    │                                                               │
                    │  ① 从 t_knowledge_document_file 读取文件字节                  │
                    │         ↓                                                     │
                    │  ② DocumentParser 解析为纯文本                                │
                    │     · PDF  → PagePdfDocumentReader（Spring AI）               │
                    │     · MD   → MarkdownDocumentReader（Spring AI）              │
                    │     · TXT  → UTF-8 直接解码                                   │
                    │         ↓                                                     │
                    │  ③ ChunkingStrategy 文本分块                                  │
                    │     · Markdown → StructureAwareChunker（结构感知）             │
                    │     · 其他     → FixedSizeChunker（固定大小，size=512）        │
                    │         ↓                                                     │
                    │  ④ for each chunk:                                            │
                    │     vectorStore.add(Document)                                 │
                    │         ↓                                                     │
                    │     Spring AI PgVectorStore 内部                              │
                    │         ↓                                                     │
                    │     CircuitBreakerEmbeddingModel.call()                       │
                    │         ↓                                                     │
                    │     ┌─────────────────────────────┐                          │
                    │     │     EmbeddingCircuitBreaker  │                          │
                    │     │  CLOSED  → 正常调用           │                          │
                    │     │  OPEN    → 跳过，试下一候选   │                          │
                    │     │  HALF_OPEN → 放行探测请求     │                          │
                    │     └──────────────┬──────────────┘                          │
                    │                   ↓                                           │
                    │     OllamaEmbeddingModel（动态切换模型名）                      │
                    │     候选1: bce-embedding-base_v1（priority=1）                 │
                    │     候选2: BAAI/bge-m3（priority=2，备用）                     │
                    │         ↓                                                     │
                    │     写入 PgVector（vector_store 表）                           │
                    │         ↓                                                     │
                    │  ⑤ 更新 t_knowledge_document.status = success / failed        │
                    └───────────────────────────────────────────────────────────────┘
```

---

## 三、新建文件清单

### 3.1 分块模块（`knowledge/chunk/`）

#### `ChunkingMode.java` — 分块模式枚举

```
FIXED_SIZE       → 固定大小滑动窗口，默认 chunkSize=512，overlap=128
STRUCTURE_AWARE  → Markdown 结构感知，target=512，max≈682，min=256
```

#### `ChunkingStrategy.java` — 分块策略接口

```java
List<String> chunk(String text, int chunkSize, int overlapSize);
ChunkingMode getType();
```

#### `chunk/strategy/FixedSizeChunker.java` — 固定大小分块器

核心特性：
- **归一化预处理**：修复 URL 被换行拆断的情况（`dingtalk.\ncom` → `dingtalk.com`）
- 修复中文软换行（`商\n保通` → `商保通`），段落换行保留不变
- **智能边界回退**：优先 `\n` > 中文句末标点（。！？）> 英文句末标点（后跟空白才算）
- 英文 `.` 不再无条件截断，避免切断 URL 域名
- 边界回退距离不超过 overlap，防止相邻块高度重复

边界回退优先级示意：

```
targetEnd 向前最多回退 overlap 个字符：
  1) 找 \n → 截到换行符后（语义最清晰）
  2) 找 。！？ → 截到标点后
  3) 找 . ! ? 且后接空白 → 截到标点后（URL 内部不算边界）
  都没找到 → 用原 targetEnd
```

#### `chunk/strategy/StructureAwareChunker.java` — 结构感知分块器

核心特性：
- 识别 4 种块类型：
  - `Heading`（`# ~######`）：标题行，独立块
  - `CodeFence`（` ``` ... ``` `）：代码围栏，整体作为一个原子块
  - `Atomic`（单行图片/链接）：`![]()` / `[]()`，独立块
  - `Para`：其他内容，按空行切段
- 按 `min/target/max` 预算打包，只在块边界切分，保持语义完整
  - `min = target / 2`，`max = target * 4 / 3`
  - 最后一块过小时自动与倒数第二块合并
- 支持 overlap：将上一块尾部内容前置到下一块开头

---

### 3.2 Embedding 模块（`knowledge/embedding/`）

#### `EmbeddingModelCandidate.java` — 候选模型配置 POJO

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | String | 唯一标识（用于熔断追踪与日志） |
| `model` | String | 实际调用的模型名 |
| `priority` | Integer | 优先级，数字越小越高 |
| `enabled` | Boolean | 是否启用（`false` 时路由直接跳过，不触发熔断） |

#### `EmbeddingProperties.java` — 配置属性类

绑定 `application.yaml` 中 `embedding` 前缀，包含：
- `candidates`：候选模型列表
- `selection.failureThreshold`：触发熔断的连续失败次数（默认 2）
- `selection.openDurationMs`：熔断持续时间（默认 30000ms）

#### `EmbeddingCircuitBreaker.java` — 三态熔断器

```
CLOSED ──[连续失败≥threshold]──▶ OPEN
  ▲                                │
  │                         [经过 openDurationMs]
  │                                ▼
  └──[探测成功]────────────── HALF_OPEN ──[探测失败]──▶ OPEN（重置计时）
```

**内部数据结构**（`BreakerEntry`）：

| 字段 | 类型 | 说明 |
|------|------|------|
| `state` | volatile State | 当前状态（CLOSED/OPEN/HALF_OPEN） |
| `failureCount` | AtomicInteger | 连续失败计数 |
| `openedAt` | AtomicLong | 进入 OPEN 状态的时间戳（ms） |
| `probeInFlight` | volatile boolean | 是否已有探测请求在途（HALF_OPEN 防并发） |

**线程安全**：`allowCall`/`markSuccess`/`markFailure` 均使用 `synchronized`，适合 Embedding 入库的低并发场景。

`allowCall` 三种响应：

| 当前状态 | 行为 |
|----------|------|
| `CLOSED` | 直接放行 |
| `OPEN` | 检查是否已过 `openDurationMs`；到了 → 转 `HALF_OPEN`，放行一个探测请求；否则 → 拒绝 |
| `HALF_OPEN` | `probeInFlight=false` → 放行，同时标记 `probeInFlight=true`；已有探测在途 → 拒绝并发 |

#### `CircuitBreakerEmbeddingModel.java` — 带熔断的 EmbeddingModel 包装器

实现 Spring AI `EmbeddingModel` 接口，包装 `OllamaEmbeddingModel`：
- 通过 `OllamaOptions.builder().model(candidate.getModel())` 动态切换模型
- 作为 `@Bean` 注入到 `VectorStore`，使 `vectorStore.add()` 全路径受熔断保护
- 实现 `embed(Document)` 和 `call(EmbeddingRequest)` 两个方法，均走熔断路由逻辑

#### `RoutingEmbeddingService.java` — 独立路由服务

与 `CircuitBreakerEmbeddingModel` 路由逻辑相同，但暴露为独立 `@Service`，供业务代码直接调用 `embed(String text)` 获取原始 `float[]` 向量（如自定义向量写入场景）。

两者共用同一个 `EmbeddingCircuitBreaker` 实例，熔断状态互通。

---

### 3.3 入库模块（`knowledge/ingestion/`）

#### `DocumentIngestionService.java` — 核心 ETL 入库服务

关键设计：
- `@Async("ingestionExecutor")`：在独立线程池中异步执行，不阻塞上传接口
- **单块失败不终止整体**：每个 chunk 单独 try-catch，失败则记录 warn 并跳过，继续处理下一块
- **元数据写入**：每个 chunk 携带 `doc_id`、`kb_id`、`doc_name`、`file_type`、`chunk_index`、`total_chunks`，支持后续按知识库过滤检索
- 全部 chunk 处理完成后统一更新文档状态为 `success` 或 `failed`

单块容错逻辑：
```java
for (int i = 0; i < chunks.size(); i++) {
    try {
        vectorStore.add(List.of(new Document(chunk, metadata)));
        successCount++;
    } catch (Exception e) {
        // 单块失败不中断整批，记录 warn 继续
        log.warn("[入库] docId={} 第 {}/{} 块写入失败，跳过。原因: {}",
                 docId, i + 1, chunks.size(), e.getMessage());
    }
}
// 全部处理完成后以 success 结束
// 注意：即使有部分块失败，最终状态仍为 success
```

> **注意**：若希望任意块失败即整批失败，需去掉单块 try-catch 让异常向上透传至外层。

---

### 3.4 配置模块（`config/`）

#### `AsyncConfig.java` — 异步线程池配置

```
ingestionExecutor:
  corePoolSize  = 2
  maxPoolSize   = 8
  queueCapacity = 100
  threadPrefix  = "ingestion-"
  rejectPolicy  = CallerRunsPolicy（队列满时同步降级，不丢任务）
```

---

## 四、修改文件清单

### `config/VectorStoreConfig.java`

**修复**：第 42 行缺少 `}` 的语法错误（原项目无法启动的根本原因）

**改造**：
- 新增 `circuitBreakerEmbeddingModel()` Bean，包装 `OllamaEmbeddingModel`
- `vectorStore()` 改为注入 `CircuitBreakerEmbeddingModel`，使向量写入路径受熔断保护
- 向量维度从 1024 升级为 **4096**（对应 `dengcao/Qwen3-Embedding-8B:F16` 模型输出维度）

```java
@Bean
public VectorStore vectorStore(JdbcTemplate jdbcTemplate,
                               CircuitBreakerEmbeddingModel circuitBreakerEmbeddingModel) {
    return PgVectorStore.builder(jdbcTemplate, circuitBreakerEmbeddingModel)
            .dimensions(4096)   // Qwen3-Embedding-8B 输出维度
            .distanceType(PgVectorStore.PgDistanceType.COSINE_DISTANCE)
            .indexType(PgVectorStore.PgIndexType.HNSW)
            .initializeSchema(true)
            .build();
}
```

### `knowledge/service/impl/KnowledgeDocumentServiceImpl.java`

在 `upload()` 方法末尾插入：

```java
// 异步触发分块 + 向量化流水线
ingestionService.ingest(docId);
```

新增 `DocumentIngestionService` 字段注入。

### `ZuoAiAgentApplication.java`

新增 `@ConfigurationPropertiesScan` 注解，激活 `EmbeddingProperties` 的自动装配。

### `application.yaml`

1. Ollama embedding 模型从 `bge-m3` 改为 `dengcao/Qwen3-Embedding-8B:F16`
2. 新增 `embedding` 配置段：

```yaml
embedding:
  selection:
    failure-threshold: 2
    open-duration-ms: 30000
  candidates:
    - id: siliconflow-bce
      model: bce-embedding-base_v1
      priority: 1
      enabled: true
    - id: siliconflow-bge-m3
      model: BAAI/bge-m3
      priority: 2
      enabled: true
```

---

## 五、熔断机制详解

### 状态转换触发条件

| 当前状态 | 触发条件 | 转换到 |
|----------|----------|--------|
| CLOSED | 连续失败 ≥ `failureThreshold`（默认2次） | OPEN |
| OPEN | 距进入 OPEN 时间 ≥ `openDurationMs`（默认30s） | HALF_OPEN |
| HALF_OPEN | 探测请求成功（`markSuccess`） | CLOSED |
| HALF_OPEN | 探测请求失败（`markFailure`） | OPEN（重置计时） |

### 路由策略

```
按 priority 升序遍历候选列表：
  if enabled == false  → 跳过（不触发熔断器）
  if allowCall() == false（OPEN）→ 跳过
  调用 OllamaEmbeddingModel（动态指定 model 名）
    成功 → markSuccess，返回向量
    失败 → markFailure，log.warn，继续下一候选
全部失败 → throw RuntimeException("所有 Embedding 候选模型均不可用")
```

### 一次完整的失败切换流程

```
chunk-1：
  → allowCall("siliconflow-bce") = true（CLOSED）
  → 调用失败 → markFailure：failureCount=1，未达阈值，仍 CLOSED

chunk-2：
  → allowCall("siliconflow-bce") = true（CLOSED）
  → 调用失败 → markFailure：failureCount=2，达到阈值 → 进入 OPEN

chunk-3：
  → allowCall("siliconflow-bce") = false（OPEN，跳过）
  → allowCall("siliconflow-bge-m3") = true（CLOSED）
  → 调用备用模型成功 → markSuccess("siliconflow-bge-m3")

30秒后，chunk-N：
  → allowCall("siliconflow-bce")：OPEN → 转 HALF_OPEN，放行探测，probeInFlight=true
  → 探测成功 → markSuccess：回到 CLOSED，failureCount 归零，probeInFlight=false
```

### 验证熔断切换

临时将 `application.yaml` 中 `siliconflow-bce` 的模型名改为无效值，上传文档后观察日志：
```
[熔断路由] 候选 siliconflow-bce 调用失败，切换下一候选
[熔断路由] 候选 siliconflow-bge-m3 调用成功
```

---

## 六、向量写入全调用链

`vectorStore.add(doc)` 看起来只是塞文档，实际内部调用链为：

```
DocumentIngestionService.ingest()
  └─ vectorStore.add(List.of(springDoc))
       │  vectorStore 实际类型：PgVectorStore（Spring AI 内置）
       ↓
  PgVectorStore.add(docs)
       │  内部对每个 Document 调用 embeddingModel.embed(doc)
       ↓
  CircuitBreakerEmbeddingModel.embed(document)    ← VectorStoreConfig 注入
       │  包装成 EmbeddingRequest，调用 call()
       ↓
  CircuitBreakerEmbeddingModel.call(request)
       │  按 priority 升序遍历候选，每个候选先问熔断器 allowCall()
       │  通过则用 OllamaOptions 动态覆盖模型名
       ↓
  OllamaEmbeddingModel.call(routed request)       ← 发 HTTP 请求到 Ollama
       │  返回 float[] 向量
       │  （Qwen3-Embedding-8B: 维度 4096；bge-m3: 维度 1024）
       ↓
  PgVectorStore 拿到向量后
       └─ INSERT INTO vector_store (content, metadata, embedding)
```

---

## 七、文件结构总览

```
src/main/java/com/example/zuoaiagent/
├── ZuoAiAgentApplication.java          ← 修改：添加 @ConfigurationPropertiesScan
├── config/
│   ├── AsyncConfig.java                ← 新建：ingestionExecutor 线程池
│   └── VectorStoreConfig.java          ← 修改：修复语法错误，注入熔断 EmbeddingModel，维度改为 4096
└── knowledge/
    ├── chunk/
    │   ├── ChunkingMode.java            ← 新建：分块模式枚举
    │   ├── ChunkingStrategy.java        ← 新建：分块策略接口
    │   └── strategy/
    │       ├── FixedSizeChunker.java    ← 新建：固定大小分块器（含 URL/CJK 归一化）
    │       └── StructureAwareChunker.java ← 新建：结构感知分块器（Markdown 友好）
    ├── embedding/
    │   ├── EmbeddingModelCandidate.java ← 新建：候选模型 POJO
    │   ├── EmbeddingProperties.java     ← 新建：配置属性类（embedding.* 前缀）
    │   ├── EmbeddingCircuitBreaker.java ← 新建：三态熔断器（per-candidate 独立状态）
    │   ├── CircuitBreakerEmbeddingModel.java ← 新建：EmbeddingModel 包装器（供 PgVectorStore 使用）
    │   └── RoutingEmbeddingService.java ← 新建：独立路由服务（供业务代码直接获取向量）
    ├── ingestion/
    │   └── DocumentIngestionService.java ← 新建：ETL 入库流水线（@Async，单块容错）
    └── service/impl/
        └── KnowledgeDocumentServiceImpl.java ← 修改：upload 后触发异步入库

src/main/resources/
└── application.yaml                    ← 修改：新增 embedding 配置段，Ollama 模型改为 Qwen3-Embedding-8B
```

---

## 八、关键类索引

| 类 | 路径 | 职责 |
|----|------|------|
| `DocumentIngestionService` | `knowledge/ingestion/` | ETL 入库流水线，调用 `vectorStore.add()`，单块容错 |
| `VectorStoreConfig` | `config/` | 装配 `PgVectorStore`（维度 4096）+ `CircuitBreakerEmbeddingModel` |
| `CircuitBreakerEmbeddingModel` | `knowledge/embedding/` | 实现 `EmbeddingModel`，供 PgVectorStore 自动调用，内含路由+熔断 |
| `RoutingEmbeddingService` | `knowledge/embedding/` | 独立 `@Service`，供业务代码直接获取原始 `float[]` 向量 |
| `EmbeddingCircuitBreaker` | `knowledge/embedding/` | 三态熔断器，按 `candidateId` 独立维护状态，`synchronized` 线程安全 |
| `EmbeddingProperties` | `knowledge/embedding/` | 绑定 `embedding.*` 配置，含候选列表和熔断参数 |
| `EmbeddingModelCandidate` | `knowledge/embedding/` | 单个候选模型配置（id/model/priority/enabled） |
| `FixedSizeChunker` | `knowledge/chunk/strategy/` | 固定大小分块器，含 URL/CJK 归一化，适用 PDF/TXT |
| `StructureAwareChunker` | `knowledge/chunk/strategy/` | 结构感知分块器，识别 Heading/Code/Atomic/Para，适用 Markdown |
| `AsyncConfig` | `config/` | ingestionExecutor 线程池，CallerRunsPolicy 防丢任务 |

---

## 九、端到端验证步骤

```bash
# 1. 启动服务（需要 Ollama 运行 Qwen3-Embedding-8B 模型）
mvn spring-boot:run

# 2. 创建知识库
curl -X POST http://localhost:8123/api/knowledge-base \
  -H "Content-Type: application/json" \
  -d '{"name":"测试知识库"}'

# 3. 上传文档（PDF 或 Markdown）
curl -X POST http://localhost:8123/api/knowledge-base/{kbId}/docs/upload \
  -F "file=@/path/to/document.pdf"

# 4. 查询文档状态（等待异步处理完成）
curl http://localhost:8123/api/knowledge-base/docs/{docId}
# 期望：status = "success"

# 5. 验证向量数据写入 PgVector
psql $DB_URL -c "SELECT COUNT(*) FROM vector_store;"
# 期望：count > 0

# 6. 验证知识库检索（RAG 对话）
curl -X POST http://localhost:8123/api/chat \
  -H "Content-Type: application/json" \
  -d '{"message":"关于文档内容的问题"}'
```

---

## 十、注意事项

1. **Ollama 需本地运行**：向量化依赖 Ollama，确保模型已加载
   ```bash
   ollama pull dengcao/Qwen3-Embedding-8B:F16
   ollama list  # 确认模型名与 application.yaml 中完全一致
   ```

2. **PgVector 维度**：`VectorStoreConfig` 当前配置 `dimensions=4096`（Qwen3-Embedding-8B），与 `application.yaml` 中 Ollama 默认模型一致。**更换模型前必须同步更新此值**，否则写入时会报维度不匹配错误。

3. **候选模型维度必须一致**：`application.yaml` 的 `embedding.candidates` 中所有候选模型的输出维度必须与 `VectorStoreConfig.dimensions` 相同。`bce-embedding-base_v1` 与 `BAAI/bge-m3` 输出维度均为 1024，**当前配置为 Ollama 本地 4096 维度模型，SiliconFlow 候选若维度不同则写入会报错**，需根据实际使用场景统一维度。

4. **Maven Lombok 问题**：项目 Maven 配置未正式注册 Lombok 注解处理器，`mvn compile` 会报预存 Lombok 错误，**请使用 IntelliJ IDEA 启动和构建**

5. **异步事务**：`upload()` 方法标注了 `@Transactional`，`@Async` 在事务提交后才触发，确保入库时文档记录已持久化

6. **部分块失败的 success 语义**：`DocumentIngestionService` 对单块异常执行 catch 跳过，即使有块写入失败，文档最终状态仍为 `success`（仅代表流水线执行完毕）。若需精确区分，可在元数据或独立字段中记录 `successCount/totalChunks`。