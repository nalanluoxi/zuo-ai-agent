package com.example.zuoaiagent.raglab.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.util.Date;

/**
 * 实验计划实体
 */
@TableName("t_rag_experiment_plan")
public class RagExperimentPlanDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String planName;

    /** 本次实验用的模型配置 ID */
    private Long modelConfigId;

    /** 本次实验用的配置版本 ID */
    private Long configVersionId;

    /** 本次实验用的提示词版本 ID */
    private Long promptVersionId;

    /** 是否使用全局题库 */
    private Boolean useGlobalQuestions;

    /** 是否使用自定义提问 */
    private Boolean useCustomQuestions;

    /** 关联的知识库ID列表（逗号分隔） */
    private String knowledgeBaseIds;

    /** 状态: PENDING / RUNNING / COMPLETED / FAILED */
    private String status;

    /** 执行结果摘要（JSON） */
    private String resultSummary;

    private Long createUserId;
    private Date createTime;
    private Date updateTime;
    private Date finishTime;

    // ===== Getters & Setters =====

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getPlanName() { return planName; }
    public void setPlanName(String planName) { this.planName = planName; }

    public Long getModelConfigId() { return modelConfigId; }
    public void setModelConfigId(Long modelConfigId) { this.modelConfigId = modelConfigId; }

    public Long getConfigVersionId() { return configVersionId; }
    public void setConfigVersionId(Long configVersionId) { this.configVersionId = configVersionId; }

    public Long getPromptVersionId() { return promptVersionId; }
    public void setPromptVersionId(Long promptVersionId) { this.promptVersionId = promptVersionId; }

    public Boolean getUseGlobalQuestions() { return useGlobalQuestions; }
    public void setUseGlobalQuestions(Boolean useGlobalQuestions) { this.useGlobalQuestions = useGlobalQuestions; }

    public Boolean getUseCustomQuestions() { return useCustomQuestions; }
    public void setUseCustomQuestions(Boolean useCustomQuestions) { this.useCustomQuestions = useCustomQuestions; }

    public String getKnowledgeBaseIds() { return knowledgeBaseIds; }
    public void setKnowledgeBaseIds(String knowledgeBaseIds) { this.knowledgeBaseIds = knowledgeBaseIds; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getResultSummary() { return resultSummary; }
    public void setResultSummary(String resultSummary) { this.resultSummary = resultSummary; }

    public Long getCreateUserId() { return createUserId; }
    public void setCreateUserId(Long createUserId) { this.createUserId = createUserId; }

    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date createTime) { this.createTime = createTime; }

    public Date getUpdateTime() { return updateTime; }
    public void setUpdateTime(Date updateTime) { this.updateTime = updateTime; }

    public Date getFinishTime() { return finishTime; }
    public void setFinishTime(Date finishTime) { this.finishTime = finishTime; }
}
