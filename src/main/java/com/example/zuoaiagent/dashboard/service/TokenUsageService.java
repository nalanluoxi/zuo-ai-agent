package com.example.zuoaiagent.dashboard.service;

/**
 * Token 使用量统计服务
 */
public interface TokenUsageService {

    /**
     * 记录 token 使用量
     *
     * @param userId 用户ID
     * @param conversationId 对话ID
     * @param messageId 消息ID
     * @param model 模型名称
     * @param promptTokens 输入 token 数
     * @param completionTokens 输出 token 数
     * @param totalTokens 总 token 数
     */
    void recordUsage(String userId, String conversationId, String messageId, String model,
                    Integer promptTokens, Integer completionTokens, Integer totalTokens);

    /**
     * 记录 token 使用量（带用途分类）
     *
     * @param userId 用户ID
     * @param conversationId 对话ID
     * @param messageId 消息ID
     * @param model 模型名称
     * @param promptTokens 输入 token 数
     * @param completionTokens 输出 token 数
     * @param totalTokens 总 token 数
     * @param usageType 用途类型：CONVERSATION / EMBEDDING / RETRIEVAL
     */
    void recordUsage(String userId, String conversationId, String messageId, String model,
                    Integer promptTokens, Integer completionTokens, Integer totalTokens, String usageType);
}
