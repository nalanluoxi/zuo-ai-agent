package com.example.zuoaiagent.knowledge.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import java.util.Date;

/**
 * 知识库实体
 */
@TableName("t_knowledge_base")
public class KnowledgeBaseDO {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 知识库名称 */
    private String name;

    /** 描述 */
    private String description;

    /** 创建人 */
    private String createdBy;

    /** 修改人 */
    private String updatedBy;

    @TableField(fill = FieldFill.INSERT)
    private Date createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;

    /** 是否删除：0-正常，1-删除 */
    @TableLogic
    private Integer deleted;

    public KnowledgeBaseDO() {
    }

    public KnowledgeBaseDO(Long id, String name, String description, String createdBy,
                            String updatedBy, Date createTime, Date updateTime, Integer deleted) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.createdBy = createdBy;
        this.updatedBy = updatedBy;
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

    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date createTime) { this.createTime = createTime; }

    public Date getUpdateTime() { return updateTime; }
    public void setUpdateTime(Date updateTime) { this.updateTime = updateTime; }

    public Integer getDeleted() { return deleted; }
    public void setDeleted(Integer deleted) { this.deleted = deleted; }

    /** Builder support */
    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private Long id;
        private String name;
        private String description;
        private String createdBy;
        private String updatedBy;
        private Date createTime;
        private Date updateTime;
        private Integer deleted;

        public Builder id(Long id) { this.id = id; return this; }
        public Builder name(String name) { this.name = name; return this; }
        public Builder description(String description) { this.description = description; return this; }
        public Builder createdBy(String createdBy) { this.createdBy = createdBy; return this; }
        public Builder updatedBy(String updatedBy) { this.updatedBy = updatedBy; return this; }
        public Builder createTime(Date createTime) { this.createTime = createTime; return this; }
        public Builder updateTime(Date updateTime) { this.updateTime = updateTime; return this; }
        public Builder deleted(Integer deleted) { this.deleted = deleted; return this; }

        public KnowledgeBaseDO build() {
            return new KnowledgeBaseDO(id, name, description, createdBy, updatedBy,
                    createTime, updateTime, deleted);
        }
    }

    @Override
    public String toString() {
        return "KnowledgeBaseDO{id=" + id + ", name='" + name + "'}";
    }
}
