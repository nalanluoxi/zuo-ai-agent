package com.example.zuoaiagent.raglab.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.util.Date;

/**
 * 生产数据回流申请
 */
@TableName("t_data_replay_request")
public class DataReplayRequestDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 来源链路 trace_id */
    private String traceId;

    /** 回捞的提问 */
    private String questionText;

    /** 来源会话 ID */
    private String sourceConversationId;

    /** 状态: PENDING / APPROVED / REJECTED */
    private String status;

    /** 审批人 ID */
    private Long approvedBy;

    /** 审批时间 */
    private Date approvedTime;

    /** 审批通过后写入 t_rag_test_question 的 ID */
    private Long targetQuestionId;

    private Long createUserId;
    private Date createTime;

    // ===== Getters & Setters =====

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTraceId() { return traceId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }

    public String getQuestionText() { return questionText; }
    public void setQuestionText(String questionText) { this.questionText = questionText; }

    public String getSourceConversationId() { return sourceConversationId; }
    public void setSourceConversationId(String sourceConversationId) { this.sourceConversationId = sourceConversationId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Long getApprovedBy() { return approvedBy; }
    public void setApprovedBy(Long approvedBy) { this.approvedBy = approvedBy; }

    public Date getApprovedTime() { return approvedTime; }
    public void setApprovedTime(Date approvedTime) { this.approvedTime = approvedTime; }

    public Long getTargetQuestionId() { return targetQuestionId; }
    public void setTargetQuestionId(Long targetQuestionId) { this.targetQuestionId = targetQuestionId; }

    public Long getCreateUserId() { return createUserId; }
    public void setCreateUserId(Long createUserId) { this.createUserId = createUserId; }

    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date createTime) { this.createTime = createTime; }
}
