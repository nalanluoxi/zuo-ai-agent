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

    /** 可见性：PRIVATE/TEAM/PUBLIC */
    private String visibility;

    /** Owner 用户 ID */
    private Long ownerId;

    /** 可读性别名（前端用） */
    private String readability;

    /** 绑定的意图节点 ID 列表 */
    private java.util.List<Long> intentNodeIds;

    /** 绑定的意图节点标签（用于展示） */
    private java.util.List<String> intentNodeLabels;

    /** 创建者用户名 */
    private String createdByUsername;

    public String getVisibility() { return visibility; }
    public void setVisibility(String visibility) { this.visibility = visibility; }

    public Long getOwnerId() { return ownerId; }
    public void setOwnerId(Long ownerId) { this.ownerId = ownerId; }

    public String getReadability() { return readability; }
    public void setReadability(String readability) { this.readability = readability; }

    public java.util.List<Long> getIntentNodeIds() { return intentNodeIds; }
    public void setIntentNodeIds(java.util.List<Long> intentNodeIds) { this.intentNodeIds = intentNodeIds; }

    public String getCreatedByUsername() { return createdByUsername; }
    public void setCreatedByUsername(String createdByUsername) { this.createdByUsername = createdByUsername; }

    public java.util.List<String> getIntentNodeLabels() { return intentNodeLabels; }
    public void setIntentNodeLabels(java.util.List<String> intentNodeLabels) { this.intentNodeLabels = intentNodeLabels; }
}
