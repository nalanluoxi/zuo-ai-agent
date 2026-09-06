package com.example.zuoaiagent.raglab.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.util.Date;

/**
 * RAG Prompt 模板（对应 t_rag_prompt_template）
 */
@TableName("t_rag_prompt_template")
public class RagPromptTemplateDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** Prompt 类型 */
    private String promptType;

    /** 模板名称 */
    private String templateName;

    /** 模板内容 */
    private String templateContent;

    /** 是否激活（0/1） */
    private Short isActive;

    /** 租户 ID */
    private Long tenantId;

    /** 创建时间 */
    private Date createTime;

    /** 更新时间 */
    private Date updateTime;

    /** 创建用户 ID */
    private Long createUserId;

    /** 更新用户 ID */
    private Long updateUserId;

    public RagPromptTemplateDO() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getPromptType() { return promptType; }
    public void setPromptType(String promptType) { this.promptType = promptType; }

    public String getTemplateName() { return templateName; }
    public void setTemplateName(String templateName) { this.templateName = templateName; }

    public String getTemplateContent() { return templateContent; }
    public void setTemplateContent(String templateContent) { this.templateContent = templateContent; }

    public Short getIsActive() { return isActive; }
    public void setIsActive(Short isActive) { this.isActive = isActive; }

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }

    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date createTime) { this.createTime = createTime; }

    public Date getUpdateTime() { return updateTime; }
    public void setUpdateTime(Date updateTime) { this.updateTime = updateTime; }

    public Long getCreateUserId() { return createUserId; }
    public void setCreateUserId(Long createUserId) { this.createUserId = createUserId; }

    public Long getUpdateUserId() { return updateUserId; }
    public void setUpdateUserId(Long updateUserId) { this.updateUserId = updateUserId; }
}
