# Embedding 模型配置指南

本项目使用 **Spring AI** 框架，向量存储为 **PgVector（PostgreSQL 扩展）**。
本文档介绍如何切换或新增 Embedding 模型，支持两种方案：**硅基流动云端 API** 和 **Ollama 本地部署**。

---

## 一、硅基流动（SiliconFlow）Embedding 配置

### 1.1 是什么

硅基流动（SiliconFlow）是国内 AI 基础设施公司，提供 **SiliconCloud** 云端模型推理平台，聚合了大量开源模型（LLM、Embedding、Rerank 等），支持 OpenAI 兼容 API。

### 1.2 注册与获取 API Key

1. 搜索"硅基流动"访问官网（siliconflow.cn）
2. 手机号注册账号
3. 进入控制台 → **API 密钥** → 创建密钥
4. 复制 API Key（格式为 `sk-xxx`）

**费用说明：**

| 模型 | 费用 |
|---|---|
| `BAAI/bge-m3` | 有免费额度，用完按 Token 计费（价格极低） |
| `Qwen/Qwen3-Embedding-8B` | 按量计费，个人开发量级几乎无感知 |

---

### 1.3 pom.xml 添加依赖

硅基流动兼容 OpenAI 接口，使用 Spring AI 的 OpenAI Starter 接入：

```xml
<!-- Spring AI OpenAI Starter（用于接入硅基流动 OpenAI 兼容接口） -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-openai-spring-boot-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

---

### 1.4 配置 application-local.yaml

在本地开发配置文件中添加：

```yaml
spring:
  ai:
    openai:
      api-key: sk-你的硅基流动密钥
      base-url: https://api.siliconflow.cn
      embedding:
        options:
          model: BAAI/bge-m3   # 或 Qwen/Qwen3-Embedding-8B
```

---

### 1.5 修改 VectorStoreConfig.java

将注入的 EmbeddingModel 改为 OpenAI 的实现，并同步调整维度：

```java
@Bean
public VectorStore vectorStore(JdbcTemplate jdbcTemplate, EmbeddingModel openAiEmbeddingModel) {
    return PgVectorStore.builder(jdbcTemplate, openAiEmbeddingModel)
            .dimensions(1024)                              // bge-m3 维度为 1024
            .distanceType(PgVectorStore.PgDistanceType.COSINE_DISTANCE)
            .indexType(PgVectorStore.PgIndexType.HNSW)
            .initializeSchema(true)
            .build();
}
```

**各模型维度说明：**

| 模型 | 维度 |
|---|---|
| `BAAI/bge-m3` | 1024 |
| `Qwen/Qwen3-Embedding-8B` | 1536 |
| `BAAI/bge-large-zh-v1.5` | 1024 |

> **注意：** 维度必须与已初始化的 PgVector 表一致。如果切换模型，需要删除 `vector_store` 表让 `initializeSchema(true)` 重建，并重新入库所有文档。

---

## 二、Ollama 本地 Embedding 配置

### 2.1 安装 Ollama

**macOS：**

```bash
# Homebrew 安装（推荐）
brew install ollama

# 或从官网下载 .dmg 安装包
```

**Linux：**

```bash
curl -fsSL https://ollama.com/install.sh | sh
```

**Windows：**

从 Ollama 官网下载 `.exe` 安装包，安装后自动注册为系统服务，开机自启。

---

### 2.2 启动 Ollama 服务

```bash
# 前台启动（方便查看日志）
ollama serve

# 验证是否正常（返回 "Ollama is running" 即成功）
curl http://localhost:11434
```

> macOS / Windows 安装包安装后，Ollama 会自动在后台运行，无需手动执行 `ollama serve`。

---

### 2.3 拉取 Embedding 模型

```bash
# 推荐：中英双语，效果好（1024 维，约 1.2GB）
ollama pull bge-m3

# 轻量版，速度快（768 维，约 274MB）
ollama pull nomic-embed-text

# 查看已下载模型
ollama list
```

---

### 2.4 测试 Embedding 接口

```bash
curl http://localhost:11434/api/embeddings \
  -d '{
    "model": "bge-m3",
    "prompt": "这是一段测试文本"
  }'
```

返回包含 `embedding` 数组的 JSON 即表示正常。

---

### 2.5 pom.xml 添加依赖

```xml
<!-- Spring AI Ollama Starter -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-ollama-spring-boot-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

---

### 2.6 配置 application-local.yaml

```yaml
spring:
  ai:
    ollama:
      base-url: http://localhost:11434
      embedding:
        options:
          model: bge-m3
```

---

### 2.7 修改 VectorStoreConfig.java

```java
@Bean
public VectorStore vectorStore(JdbcTemplate jdbcTemplate, EmbeddingModel ollamaEmbeddingModel) {
    return PgVectorStore.builder(jdbcTemplate, ollamaEmbeddingModel)
            .dimensions(1024)                              // bge-m3 维度为 1024
            .distanceType(PgVectorStore.PgDistanceType.COSINE_DISTANCE)
            .indexType(PgVectorStore.PgIndexType.HNSW)
            .initializeSchema(true)
            .build();
}
```

---

### 2.8 常用 Ollama 命令

```bash
ollama list              # 查看已下载的所有模型
ollama pull <model>      # 拉取/更新模型
ollama rm <model>        # 删除模型，释放磁盘空间
ollama ps                # 查看当前加载到内存的模型
ollama stop <model>      # 卸载模型，释放显存/内存
ollama show <model>      # 查看模型详情（维度、参数等）
```

---

## 三、两种方案对比

| 对比项 | 硅基流动（云端） | Ollama（本地） |
|---|---|---|
| 费用 | 免费额度 + 按量付费 | 完全免费 |
| 网络依赖 | 需要公网访问 | 完全离线 |
| 推理速度 | 快（云端 GPU） | 取决于本机硬件 |
| 数据隐私 | 数据上传至云端 | 数据不离本机 |
| 配置复杂度 | 低（注册拿 Key 即用） | 中（需本机安装） |
| 推荐场景 | 生产环境 / 协作开发 | 本地开发 / 数据敏感场景 |

---

## 四、当前项目默认配置（DashScope）

项目默认使用阿里云 DashScope 的 Embedding 模型，配置如下：

```yaml
# application.yaml
spring:
  ai:
    dashscope:
      api-key: ${DASHSCOPE_API_KEY}
      # embedding 默认使用 text-embedding-v3，维度 1536
```

```java
// VectorStoreConfig.java
// 注入的是 dashscopeEmbeddingModel，维度 1536
PgVectorStore.builder(jdbcTemplate, dashscopeEmbeddingModel)
    .dimensions(1536)
```

如需切换方案，按上述步骤修改对应的 `EmbeddingModel` 注入 Bean 名称和维度即可。

---

## 五、注意事项

1. **维度必须统一**：向量表的维度在创建时固定，切换模型后必须**删表重建**并重新入库所有文档。
2. **Bean 名称**：Spring AI 多 Starter 共存时，注入 `EmbeddingModel` 需使用具体 Bean 名称区分（`dashscopeEmbeddingModel` / `openAiEmbeddingModel` / `ollamaEmbeddingModel`）。
3. **Ollama 内存要求**：`bge-m3` 约需 2GB 内存，`nomic-embed-text` 约需 500MB，请根据机器配置选择。
4. **PgVector 扩展**：数据库需安装 pgvector 扩展，Supabase 已内置，本地 PostgreSQL 需手动安装（`CREATE EXTENSION vector`）。