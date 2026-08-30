package com.example.zuoaiagent.feedback.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.zuoaiagent.feedback.entity.MessageFeedbackDO;
import com.example.zuoaiagent.feedback.mapper.MessageFeedbackMapper;
import com.example.zuoaiagent.feedback.service.MessageFeedbackService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 消息反馈服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MessageFeedbackServiceImpl implements MessageFeedbackService {
    
    private final MessageFeedbackMapper feedbackMapper;
    
    @Override
    public boolean submitFeedback(String conversationId, String messageId, Integer feedbackType) {
        try {
            Long userId = StpUtil.getLoginIdAsLong();
            
            // 检查是否已存在反馈
            LambdaQueryWrapper<MessageFeedbackDO> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(MessageFeedbackDO::getConversationId, conversationId)
                   .eq(MessageFeedbackDO::getMessageId, messageId)
                   .eq(MessageFeedbackDO::getUserId, userId);
            
            MessageFeedbackDO existing = feedbackMapper.selectOne(wrapper);
            
            if (existing != null) {
                // 更新现有反馈
                existing.setFeedbackType(feedbackType);
                return feedbackMapper.updateById(existing) > 0;
            }
            
            // 创建新反馈
            MessageFeedbackDO feedback = new MessageFeedbackDO();
            feedback.setConversationId(conversationId);
            feedback.setMessageId(messageId);
            feedback.setUserId(userId);
            feedback.setFeedbackType(feedbackType);
            
            return feedbackMapper.insert(feedback) > 0;
        } catch (Exception e) {
            log.error("提交反馈失败：", e);
            return false;
        }
    }
    
    @Override
    public Map<String, Object> getFeedbackStats(String conversationId) {
        try {
            LambdaQueryWrapper<MessageFeedbackDO> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(MessageFeedbackDO::getConversationId, conversationId);
            
            List<MessageFeedbackDO> feedbacks = feedbackMapper.selectList(wrapper);
            
            long likes = feedbacks.stream().filter(f -> f.getFeedbackType() == 1).count();
            long dislikes = feedbacks.stream().filter(f -> f.getFeedbackType() == 0).count();
            
            return Map.of(
                "conversationId", conversationId,
                "totalFeedback", feedbacks.size(),
                "likes", likes,
                "dislikes", dislikes,
                "likeRate", feedbacks.isEmpty() ? 0 : (likes * 100.0 / feedbacks.size())
            );
        } catch (Exception e) {
            log.error("获取反馈统计失败：", e);
            return Map.of(
                "conversationId", conversationId,
                "totalFeedback", 0,
                "likes", 0L,
                "dislikes", 0L,
                "likeRate", 0.0
            );
        }
    }
}
