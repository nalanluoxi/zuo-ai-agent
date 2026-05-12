package com.example.zuoaiagent.knowledge.embedding;

/**
 * Embedding 模型候选配置 POJO
 *
 * <p>对应 {@code application.yaml} 中 {@code embedding.candidates} 列表的单个条目，
 * 由 {@link EmbeddingProperties} 统一注入。
 *
 * <p>参照 ragent {@code infra-ai} 模块中的模型候选设计，每个候选对象拥有独立的
 * 优先级和启用开关，{@link EmbeddingCircuitBreaker} 按 {@code id} 追踪熔断状态。
 */
public class EmbeddingModelCandidate {

    /**
     * 候选模型唯一标识，用于熔断器追踪与日志输出。
     * 示例：{@code siliconflow-bce}
     */
    private String id;

    /**
     * 实际调用 Embedding API 时传入的模型名称。
     * 示例：{@code bce-embedding-base_v1}、{@code BAAI/bge-m3}
     */
    private String model;

    /**
     * 底层 Embedding 服务提供商。
     * 支持 {@code "ollama"}（本地 Ollama）和 {@code "openai"}（OpenAI 兼容接口，如硅基流动）。
     */
    private String provider;

    /**
     * 优先级：数字越小越高，路由时按升序排列后依次尝试。
     */
    private Integer priority;

    /**
     * 是否启用。设为 {@code false} 时路由器直接跳过，不触发熔断器逻辑。
     */
    private Boolean enabled;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public Integer getPriority() {
        return priority;
    }

    public void setPriority(Integer priority) {
        this.priority = priority;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }
}
