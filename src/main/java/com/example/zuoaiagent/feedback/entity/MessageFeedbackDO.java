package com.example.zuoaiagent.feedback.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 消息反馈实体类
 */
@Data
@TableName("t_message_feedback")
public class MessageFeedbackDO {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    /**
     * 链路追踪 ID
     */
    private String traceId;
    
    /**
     * 会话 ID
     */
    private String conversationId;
    
    /**
     * 消息 ID
     */
    private String messageId;
    
    /**
     * 用户 ID
     */
    private Long userId;
    
    /**
     * 反馈类型：1=点赞, 0=点踩
     */
    private Integer feedbackType;
    
    /**
     * 创建时间
     */
    private Date createdAt;
}
