package com.example.zuoaiagent.knowledge.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import java.util.Date;

@TableName("t_knowledge_base")
public class KnowledgeBaseDO {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String name;

    private String description;

    private String createdBy;

    private String updatedBy;

    private Long tenantId;

    private Long teamId;

    private Long ownerId;

    private String visibility = "PRIVATE";

    private Integer enabled;

    private Integer docCount;

    private Integer chunkCount;

    @TableField(fill = FieldFill.INSERT)
    private Date createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;

    @TableLogic
    private Integer deleted;

    public KnowledgeBaseDO() {
    }

    public KnowledgeBaseDO(Long id, String name, String description, String createdBy,
                            String updatedBy, Long tenantId, Long teamId, Long ownerId,
                            String visibility, Integer enabled, Integer docCount, Integer chunkCount,
                            Date createTime, Date updateTime, Integer deleted) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.createdBy = createdBy;
        this.updatedBy = updatedBy;
        this.tenantId = tenantId;
        this.teamId = teamId;
        this.ownerId = ownerId;
        this.visibility = visibility;
        this.enabled = enabled;
        this.docCount = docCount;
        this.chunkCount = chunkCount;
        this.createTime = createTime;
        this.updateTime = updateTime;
        this.deleted = deleted;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }

    public Long getTeamId() { return teamId; }
    public void setTeamId(Long teamId) { this.teamId = teamId; }

    public Long getOwnerId() { return ownerId; }
    public void setOwnerId(Long ownerId) { this.ownerId = ownerId; }

    public String getVisibility() { return visibility; }
    public void setVisibility(String visibility) { this.visibility = visibility; }

    public Integer getEnabled() { return enabled; }
    public void setEnabled(Integer enabled) { this.enabled = enabled; }

    public Integer getDocCount() { return docCount; }
    public void setDocCount(Integer docCount) { this.docCount = docCount; }

    public Integer getChunkCount() { return chunkCount; }
    public void setChunkCount(Integer chunkCount) { this.chunkCount = chunkCount; }

    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date createTime) { this.createTime = createTime; }

    public Date getUpdateTime() { return updateTime; }
    public void setUpdateTime(Date updateTime) { this.updateTime = updateTime; }

    public Integer getDeleted() { return deleted; }
    public void setDeleted(Integer deleted) { this.deleted = deleted; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private Long id;
        private String name;
        private String description;
        private String createdBy;
        private String updatedBy;
        private Long tenantId;
        private Long teamId;
        private Long ownerId;
        private String visibility = "PRIVATE";
        private Integer enabled;
        private Integer docCount;
        private Integer chunkCount;
        private Date createTime;
        private Date updateTime;
        private Integer deleted;

        public Builder id(Long id) { this.id = id; return this; }
        public Builder name(String name) { this.name = name; return this; }
        public Builder description(String description) { this.description = description; return this; }
        public Builder createdBy(String createdBy) { this.createdBy = createdBy; return this; }
        public Builder updatedBy(String updatedBy) { this.updatedBy = updatedBy; return this; }
        public Builder tenantId(Long tenantId) { this.tenantId = tenantId; return this; }
        public Builder teamId(Long teamId) { this.teamId = teamId; return this; }
        public Builder ownerId(Long ownerId) { this.ownerId = ownerId; return this; }
        public Builder visibility(String visibility) { this.visibility = visibility; return this; }
        public Builder enabled(Integer enabled) { this.enabled = enabled; return this; }
        public Builder docCount(Integer docCount) { this.docCount = docCount; return this; }
        public Builder chunkCount(Integer chunkCount) { this.chunkCount = chunkCount; return this; }
        public Builder createTime(Date createTime) { this.createTime = createTime; return this; }
        public Builder updateTime(Date updateTime) { this.updateTime = updateTime; return this; }
        public Builder deleted(Integer deleted) { this.deleted = deleted; return this; }

        public KnowledgeBaseDO build() {
            return new KnowledgeBaseDO(id, name, description, createdBy, updatedBy,
                    tenantId, teamId, ownerId, visibility, enabled, docCount, chunkCount,
                    createTime, updateTime, deleted);
        }
    }

    @Override
    public String toString() {
        return "KnowledgeBaseDO{id=" + id + ", name='" + name + "'}";
    }
}