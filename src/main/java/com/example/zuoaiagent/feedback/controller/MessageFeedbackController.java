package com.example.zuoaiagent.feedback.controller;

import com.example.zuoaiagent.common.BaseResponse;
import com.example.zuoaiagent.common.ResultUtils;
import com.example.zuoaiagent.feedback.service.MessageFeedbackService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 消息反馈控制器
 */
@RestController
@RequestMapping("/feedback")
@RequiredArgsConstructor
public class MessageFeedbackController {
    
    private final MessageFeedbackService feedbackService;
    
    /**
     * 提交反馈
     * @param conversationId 会话 ID
     * @param messageId 消息 ID
     * @param feedbackType 反馈类型：1=点赞, 0=点踩
     */
    @PostMapping
    public BaseResponse<Boolean> submitFeedback(@RequestBody Map<String, Object> body) {
        String conversationId = (String) body.get("conversationId");
        String messageId = (String) body.get("messageId");
        Integer feedbackType = (Integer) body.get("feedbackType");
        return ResultUtils.success(feedbackService.submitFeedback(conversationId, messageId, feedbackType));
    }
    
    /**
     * 获取反馈统计
     * @param conversationId 会话 ID
     */
    @GetMapping
    public BaseResponse<Map<String, Object>> getFeedbackStats(@RequestParam String conversationId) {
        return ResultUtils.success(feedbackService.getFeedbackStats(conversationId));
    }
}
