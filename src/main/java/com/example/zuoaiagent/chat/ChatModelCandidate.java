package com.example.zuoaiagent.chat;

/**
 * Chat 模型候选配置 POJO
 *
 * <p>对应 {@code application.yaml} 中 {@code chat.candidates} 列表的单个条目，
 * 由 {@link ChatModelProperties} 统一注入。
 */
public class ChatModelCandidate {

    /** 候选模型唯一标识，用于熔断器追踪与日志输出。示例：{@code dashscope-qwen} */
    private String id;

    /** 实际调用 Chat API 时传入的模型名称。示例：{@code qwen-plus} */
    private String model;

    /**
     * 底层 Chat 服务提供商。
     * 支持 {@code "dashscope"}（阿里云百炼）和 {@code "openai"}（OpenAI 兼容接口）。
     */
    private String provider;

    /** 优先级：数字越小越高，路由时按升序排列后依次尝试。 */
    private Integer priority;

    /** 是否启用。设为 {@code false} 时路由器直接跳过，不触发熔断器逻辑。 */
    private Boolean enabled;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }

    public Integer getPriority() { return priority; }
    public void setPriority(Integer priority) { this.priority = priority; }

    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
}
