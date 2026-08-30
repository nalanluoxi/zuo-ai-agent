package com.example.zuoaiagent.feedback.service;

import java.util.Map;

/**
 * 消息反馈服务接口
 */
public interface MessageFeedbackService {
    
    /**
     * 提交反馈
     * @param conversationId 会话 ID
     * @param messageId 消息 ID
     * @param feedbackType 反馈类型：1=点赞, 0=点踩
     * @return 是否成功
     */
    boolean submitFeedback(String conversationId, String messageId, Integer feedbackType);
    
    /**
     * 获取反馈统计
     * @param conversationId 会话 ID
     * @return 反馈统计
     */
    Map<String, Object> getFeedbackStats(String conversationId);
}
