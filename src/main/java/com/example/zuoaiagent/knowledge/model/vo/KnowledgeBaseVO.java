package com.example.zuoaiagent.knowledge.model.vo;

import java.util.Date;

public class KnowledgeBaseVO {

    private Long id;

    private String name;

    private String description;

    /** 文档数量 */
    private Long documentCount;

    private String createdBy;

    private Date createTime;

    private Date updateTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Long getDocumentCount() { return documentCount; }
    public void setDocumentCount(Long documentCount) { this.documentCount = documentCount; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date createTime) { this.createTime = createTime; }

    public Date getUpdateTime() { return updateTime; }
    public void setUpdateTime(Date updateTime) { this.updateTime = updateTime; }
}
