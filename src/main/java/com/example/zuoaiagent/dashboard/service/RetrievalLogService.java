package com.example.zuoaiagent.dashboard.service;

/**
 * 检索日志统计服务
 */
public interface RetrievalLogService {

    /**
     * 记录检索日志
     *
     * @param userId 用户ID
     * @param conversationId 对话ID
     * @param messageId 消息ID
     * @param knowledgeBaseId 知识库ID
     * @param queryText 查询内容
     * @param resultCount 结果数量
     * @param relevanceScore 相关性分数
     * @param latencyMs 延迟毫秒数
     */
    void recordRetrieval(String userId, String conversationId, String messageId,
                        String knowledgeBaseId, String queryText, Integer resultCount,
                        Double relevanceScore, Integer latencyMs);
}
