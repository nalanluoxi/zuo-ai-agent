package com.example.zuoaiagent.raglab.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.util.Date;

/**
 * RAG Prompt 版本（对应 t_rag_prompt_version）
 */
@TableName("t_rag_prompt_version")
public class RagPromptVersionDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** Prompt ID */
    private Long promptId;

    /** 版本号 */
    private Integer versionNo;

    /** 模板内容 */
    private String templateContent;

    /** 变更日志 */
    private String changeLog;

    /** 创建时间 */
    private Date createTime;

    /** 创建用户 ID */
    private Long createUserId;

    /** 租户 ID */
    private Long tenantId;

    public RagPromptVersionDO() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getPromptId() { return promptId; }
    public void setPromptId(Long promptId) { this.promptId = promptId; }

    public Integer getVersionNo() { return versionNo; }
    public void setVersionNo(Integer versionNo) { this.versionNo = versionNo; }

    public String getTemplateContent() { return templateContent; }
    public void setTemplateContent(String templateContent) { this.templateContent = templateContent; }

    public String getChangeLog() { return changeLog; }
    public void setChangeLog(String changeLog) { this.changeLog = changeLog; }

    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date createTime) { this.createTime = createTime; }

    public Long getCreateUserId() { return createUserId; }
    public void setCreateUserId(Long createUserId) { this.createUserId = createUserId; }

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
}
