package com.example.zuoaiagent.trace.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import java.util.Date;

/**
 * RAG 流水线级别追踪记录（对应 t_rag_trace_run）
 *
 * <p>每次完整的 SmartRagPipeline 调用产生一条记录，
 * 记录整体状态、总耗时、原始问题等。
 */
@TableName("t_rag_trace_run")
public class RagTraceRunDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 全局链路 ID（UUID，贯穿所有 node 记录） */
    private String traceId;

    /** 会话 ID */
    private String conversationId;

    /** 用户原始问题 */
    private String originalPrompt;

    /** 状态：RUNNING / SUCCESS / ERROR */
    private String status;

    /** 错误信息 */
    private String errorMessage;

    /** 流水线开始时间 */
    private Date startTime;

    /** 流水线结束时间 */
    private Date endTime;

    /** 总耗时（毫秒） */
    private Long durationMs;

    /** 灰度标签：BASELINE / TAG_A / TAG_B */
    private String grayTag;

    /** 关联的实验记录 ID */
    private Long experimentId;

    @TableField(fill = FieldFill.INSERT)
    private Date createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;

    @TableLogic
    private Short deleted;

    public RagTraceRunDO() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTraceId() { return traceId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }

    public String getConversationId() { return conversationId; }
    public void setConversationId(String conversationId) { this.conversationId = conversationId; }

    public String getOriginalPrompt() { return originalPrompt; }
    public void setOriginalPrompt(String originalPrompt) { this.originalPrompt = originalPrompt; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public Date getStartTime() { return startTime; }
    public void setStartTime(Date startTime) { this.startTime = startTime; }

    public Date getEndTime() { return endTime; }
    public void setEndTime(Date endTime) { this.endTime = endTime; }

    public Long getDurationMs() { return durationMs; }
    public void setDurationMs(Long durationMs) { this.durationMs = durationMs; }

    public String getGrayTag() { return grayTag; }
    public void setGrayTag(String grayTag) { this.grayTag = grayTag; }

    public Long getExperimentId() { return experimentId; }
    public void setExperimentId(Long experimentId) { this.experimentId = experimentId; }

    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date createTime) { this.createTime = createTime; }

    public Date getUpdateTime() { return updateTime; }
    public void setUpdateTime(Date updateTime) { this.updateTime = updateTime; }

    public Short getDeleted() { return deleted; }
    public void setDeleted(Short deleted) { this.deleted = deleted; }
}
