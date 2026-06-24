# 分块策略对比文档

## 概述

文本分块（Chunking）是 RAG 入库流水线中的核心环节，决定了向量单元的粒度。
分块太大 → 单次检索携带冗余信息，干扰 LLM 生成；
分块太小 → 语义不完整，检索召回率下降。

本文档对比 `ragent` 和 `zuo-ai-agent` 两个项目中的分块策略实现，帮助选择合适的策略。

---

## 一、策略总览

| 策略 | ragent 实现 | zuo-ai-agent 实现 | 适用文档类型 |
|------|-------------|-------------------|------------|
| 固定大小（重叠） | `FixedSizeTextChunker` | `FixedSizeChunker` | PDF、TXT、无结构纯文本 |
| 结构感知（Markdown） | `StructureAwareTextChunker` | `StructureAwareChunker` | Markdown、带标题的文档 |
| Spring AI Token | — | `MyTokenTextSplitter` | 通用（基于 token 计数） |

---

## 二、FixedSizeChunker（固定大小分块）

### 核心参数

| 参数 | 含义 | 默认值（zuo-ai-agent） |
|------|------|----------------------|
| `chunkSize` | 每块目标最大字符数 | 512（可通过 `ingestion.chunking.chunk-size` 配置） |
| `overlapSize` | 相邻块重叠字符数 | 128（可通过 `ingestion.chunking.overlap-size` 配置） |

### 边界对齐优先级（两个项目一致）

```
换行符 > 中文句末标点（。！？） > 英文句末标点（后跟空白）
```

在 `targetEnd` 位置向前回退最多 `overlapSize` 个字符寻找语义边界，减少在句中截断的概率。

### 额外增强（zuo-ai-agent FixedSizeChunker）

- **URL 断行修复**：检测 `http://`/`https://` 起始的 URL，自动合并被换行拆散的 URL（如 `dingtalk.\ncom`）。
- **中文软换行修复**：前后均为 CJK 汉字时，认为是 PDF 提取产生的软换行，静默去除。
- **英文 `.` 保护**：仅在后跟空白时视为句末，避免切断 URL 域名（如 `spring.io`）。

### 适用场景

- 扫描版 PDF 经 OCR 提取的文本（无结构）
- 普通 TXT 文件
- HTML 转换后的纯文本

---

## 三、StructureAwareChunker（结构感知分块）

### 核心参数

| 参数 | 含义 | 默认值 |
|------|------|--------|
| `chunkSize`（target） | 目标块大小（字符数） | 512 |
| `overlapSize` | 追加到下一块开头的重叠字符数 | 128 |
| min | target / 2 | 256 |
| max | target * 4 / 3 | ~683 |

### 识别的块类型

| 类型 | 识别规则 | 处理方式 |
|------|---------|---------|
| `HEADING` | `^#{1,6}\s+.*$` | 单独成块，不合并 |
| `CODE` | ` ```...``` ` 代码围栏 | 整体作为一个块，不拆分 |
| `ATOMIC` | 单行图片 `![]()`、单行链接 `[]()` | 单独成块 |
| `PARA` | 其他内容，按空行切段 | 按 min/target/max 预算合并相邻段落 |

### 打包逻辑

1. 扫描全文，识别各类结构块（`Block`）
2. 贪心合并相邻块：累计长度 ≤ max 则继续合并，超过 max 时若当前 size < min 则忍一次超限
3. 最后一块过小（< min/2）时尝试与前一块合并（避免尾部碎块）
4. 物化时在块开头追加上一块尾部的 overlap 字符

### 适用场景

- Markdown 格式的知识文档、API 文档、README
- 有明显标题层级的文档
- 代码片段需保持完整的技术文档

---

## 四、MyTokenTextSplitter（Token 分块，Spring AI 原生）

### 核心参数（TokenTextSplitter 构造函数）

| 参数 | 含义 | 默认值 |
|------|------|--------|
| `defaultChunkSize` | 每块最大 token 数 | 1000 |
| `minChunkSizeChars` | chunk 最小字符数（低于此不拆分） | 400 |
| `minChunkLengthToEmbed` | 低于此长度的 chunk 直接丢弃 | 10 |
| `maxNumChunks` | 最多切出的 chunk 数 | 5000 |
| `keepSeparator` | 是否保留分隔符到 chunk 中 | true |

### 与 FixedSizeChunker 的区别

- Token 分块以 **token 数** 为单位（1 个汉字 ≈ 2-3 token），更贴近 LLM 的上下文限制
- 但 token 计算依赖 tokenizer，有额外开销
- `FixedSizeChunker` 以字符数为单位，更直观可控

### 使用方式

```java
// 默认参数
myTokenTextSplitter.splitDocuments(documents);

// 自定义参数
myTokenTextSplitter.splitCustomized(documents);  // 1000 token, overlap=400 chars
```

---

## 五、策略选择建议

```
文档类型
  ├── Markdown / 有标题层级
  │     → StructureAwareChunker（保留代码块完整性，标题单独成块）
  │
  ├── PDF（文字图层）/ TXT
  │     → FixedSizeChunker（chunkSize=512, overlapSize=128）
  │
  ├── PDF（图片型，经 OCR）
  │     → FixedSizeChunker（chunkSize=256~512，因 OCR 文本质量较低）
  │
  └── 需精确控制 token 数（接近模型限制）
        → MyTokenTextSplitter（defaultChunkSize 按模型窗口调整）
```

---

## 六、元数据增强（可选）

入库时可选开启以下增强，通过 `ingestion.enrichment` 配置：

| 增强类型 | 配置项 | 效果 | 代价 |
|---------|-------|------|------|
| 关键词增强 | `enable-keyword: true`（默认开启） | 每块提取关键词存入 metadata `excerpt_keywords` | 每块 1 次 LLM 调用 |
| 摘要增强 | `enable-summary: false`（默认关闭） | 生成 CURRENT/PREVIOUS/NEXT 三种摘要写入 metadata | 每块 1-3 次 LLM 调用，耗时较高 |

关键词增强可提升向量检索的精度（关键词被 embed 进向量空间）；
摘要增强可缓解跨块语义断裂问题，适合长文档精细检索场景。

---

## 七、ragent vs zuo-ai-agent 差异汇总

| 维度 | ragent | zuo-ai-agent |
|------|--------|-------------|
| 分块触发 | RocketMQ 事务消息（`KnowledgeDocumentChunkEvent`） | `@Async` 线程池直接调用 |
| 分块策略 | `ChunkerNode`（流水线节点） | `DocumentIngestionService`（单一服务类） |
| 参数配置 | YAML → `@ConfigurationProperties` | YAML → `IngestionProperties` |
| 增强节点 | `EnhancerNode`（关键词）+ `EnricherNode`（摘要） | `MyDocumentEnricher`（关键词 + 摘要合并，受 `IngestionProperties` 控制） |
| 向量写入 | `IndexerNode` → `PgVectorStoreService` / `MilvusVectorStoreService` | `DocumentIngestionService` → `VectorStore`（PgVector） |
| 状态追踪 | DB 状态字段 + RocketMQ 重试 | DB 状态字段（success/failed），无重试 |