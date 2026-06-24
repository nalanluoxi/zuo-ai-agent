# 向量写入与 Embedding 熔断机制详解

本文档分析 `vectorStore.add()` 背后的完整调用链，以及 Embedding 模型的路由与熔断机制。

---

## 一、一句话总结

`vectorStore.add(doc)` 看起来只是塞文档，实际上内部走了：

> **Spring AI PgVectorStore → CircuitBreakerEmbeddingModel → EmbeddingCircuitBreaker 熔断检查 → OllamaEmbeddingModel 动态路由 → 写入 PostgreSQL pgvector**

---

## 二、调用链全景

```
DocumentIngestionService.ingest()
  └─ vectorStore.add(List.of(springDoc))          ← 业务代码看到的唯一入口
       │
       │  vectorStore 实际类型：PgVectorStore（Spring AI 内置）
       ↓
  PgVectorStore.add(docs)
       │  内部对每个 Document 调用 embeddingModel.embed(doc)
       ↓
  CircuitBreakerEmbeddingModel.embed(document)    ← VectorStoreConfig 注入的 EmbeddingModel
       │  包装成 EmbeddingRequest，调用 call()
       ↓
  CircuitBreakerEmbeddingModel.call(request)
       │  按 priority 升序遍历候选列表
       │  每个候选先问熔断器 allowCall()
       │  通过则用 OllamaOptions 动态覆盖模型名
       ↓
  OllamaEmbeddingModel.call(routed request)       ← 真正发 HTTP 请求到 Ollama
       │
       ↓
  Ollama 本地服务（bge-m3 等模型）
       │  返回 float[] 向量（维度由模型决定，如 bge-m3=1024，Qwen3-Embedding-8B=4096）
       ↓
  PgVectorStore 拿到向量后
       └─ INSERT INTO vector_store (content, metadata, embedding)
```

---

## 三、VectorStore 如何绑定 EmbeddingModel

`VectorStoreConfig` 手动构建 `PgVectorStore`，将 `CircuitBreakerEmbeddingModel` 直接传入：

```java
// VectorStoreConfig.java
@Bean
public VectorStore vectorStore(JdbcTemplate jdbcTemplate,
                               CircuitBreakerEmbeddingModel circuitBreakerEmbeddingModel) {
    return PgVectorStore.builder(jdbcTemplate, circuitBreakerEmbeddingModel)
            .dimensions(4096)                           // 与模型输出维度一致
            .distanceType(PgVectorStore.PgDistanceType.COSINE_DISTANCE)
            .indexType(PgVectorStore.PgIndexType.HNSW)
            .initializeSchema(true)
            .build();
}
```

因此 `vectorStore.add()` 触发的向量化，走的是 `CircuitBreakerEmbeddingModel`，而非 Spring AI 原生自动装配的 `OllamaEmbeddingModel`。

---

## 四、熔断机制详解

熔断状态由 `EmbeddingCircuitBreaker` 管理，**每个候选模型独立维护一套三态状态机**，互不干扰。

### 4.1 状态转换图

```
          连续失败 >= failureThreshold（默认2次）
CLOSED ─────────────────────────────────────────→ OPEN
  ↑                                                  │
  │ 探测成功（markSuccess）          等待 openDurationMs（默认30秒）
  │                                                  │
  └────────────────── HALF_OPEN ←────────────────────┘
                          │
                          │ 探测失败（markFailure）
                          └──────────────────────────→ OPEN（重置计时）
```

### 4.2 `allowCall` 三种响应

| 当前状态 | 行为 |
|----------|------|
| `CLOSED` | 直接放行 |
| `OPEN` | 检查时间窗口：未到 → 拒绝；到了 → 转 `HALF_OPEN`，放行一个探测请求 |
| `HALF_OPEN` | 已有探测在途（`probeInFlight=true`）→ 拒绝并发；否则放行 |

### 4.3 一次完整的失败切换流程示例

```
chunk-1：
  → allowCall("ollama-bge-m3") = true（CLOSED）
  → 调用失败 → markFailure：failureCount=1，未达阈值，仍 CLOSED

chunk-2：
  → allowCall("ollama-bge-m3") = true（CLOSED）
  → 调用失败 → markFailure：failureCount=2，达到阈值，进入 OPEN

chunk-3：
  → allowCall("ollama-bge-m3") = false（OPEN，跳过）
  → allowCall("ollama-fallback") = true（CLOSED）
  → 调用备用模型成功 → markSuccess("ollama-fallback")

30秒后，chunk-N：
  → allowCall("ollama-bge-m3")：OPEN → 转 HALF_OPEN，放行探测
  → 探测成功 → markSuccess：回到 CLOSED，failureCount 归零
```

### 4.4 线程安全实现

`allowCall` / `markSuccess` / `markFailure` 三个方法均加 `synchronized`，结合 `AtomicInteger`/`AtomicLong` 保证状态一致性。适合 Embedding 入库这类低并发场景。

---

## 五、多候选模型路由

### 5.1 候选列表配置

```yaml
embedding:
  selection:
    failure-threshold: 2      # 连续失败几次触发熔断
    open-duration-ms: 30000   # 熔断持续时长（毫秒）
  candidates:
    - id: ollama-bge-m3
      model: bge-m3
      priority: 1
      enabled: true
    - id: ollama-fallback
      model: nomic-embed-text
      priority: 2
      enabled: true
```

### 5.2 路由逻辑（CircuitBreakerEmbeddingModel.call）

```
getSortedCandidates()：按 priority 升序排列，过滤 enabled=false 的候选

for each candidate:
  if enabled=false → 跳过
  if !circuitBreaker.allowCall(id) → 跳过（熔断中）
  构造 EmbeddingRequest，通过 OllamaOptions 动态覆盖 model 名
  delegate.call(routed request)
    成功 → markSuccess，return response
    失败 → markFailure，继续尝试下一候选

全部失败 → throw RuntimeException("所有 Embedding 候选模型均不可用")
```

---

## 六、两套 Embedding 入口的区别

项目中存在两套 Embedding 调用路径：

| | `CircuitBreakerEmbeddingModel` | `RoutingEmbeddingService` |
|---|---|---|
| **类型** | 实现 `EmbeddingModel` 接口的包装器 | 独立 Spring `@Service` |
| **谁在用** | `PgVectorStore`（`vectorStore.add()` 自动调用） | 业务代码直接注入，手动调 `embed(text)` |
| **触发时机** | 写向量库时由 Spring AI 框架自动触发 | 需要手动获取原始 `float[]` 向量时 |
| **熔断共享** | 共用同一个 `EmbeddingCircuitBreaker` 实例，熔断状态互通 ||

---

## 七、单块失败容错

`DocumentIngestionService` 对每个 chunk 单独 try-catch，单块向量化失败不中断整批入库：

```java
for (int i = 0; i < chunks.size(); i++) {
    try {
        vectorStore.add(List.of(new Document(chunk, metadata)));
        successCount++;
    } catch (Exception e) {
        // 单块失败：记录 warn，继续处理下一块
        log.warn("[入库] 第 {}/{} 块写入失败，跳过。原因: {}", i+1, chunks.size(), e.getMessage());
    }
}
```

所有候选均不可用时，`CircuitBreakerEmbeddingModel` 抛出 `RuntimeException`，被此处 catch 捕获，该块跳过，文档最终状态仍会记录 `success`（部分块失败）。若希望整批失败则需将异常向外透传。

---

## 八、关键类索引

| 类 | 路径 | 职责 |
|----|------|------|
| `DocumentIngestionService` | `knowledge/ingestion/` | ETL 入库流水线，调用 `vectorStore.add()` |
| `VectorStoreConfig` | `config/` | 装配 `PgVectorStore` + `CircuitBreakerEmbeddingModel` |
| `CircuitBreakerEmbeddingModel` | `knowledge/embedding/` | 实现 `EmbeddingModel`，供 PgVectorStore 使用，内含路由+熔断 |
| `RoutingEmbeddingService` | `knowledge/embedding/` | 独立服务，供业务代码直接获取 `float[]` 向量 |
| `EmbeddingCircuitBreaker` | `knowledge/embedding/` | 三态熔断器，按候选 id 独立维护状态 |
| `EmbeddingProperties` | `knowledge/embedding/` | 绑定 `embedding.*` 配置，含候选列表和熔断参数 |
| `EmbeddingModelCandidate` | `knowledge/embedding/` | 单个候选模型配置（id/model/priority/enabled） |