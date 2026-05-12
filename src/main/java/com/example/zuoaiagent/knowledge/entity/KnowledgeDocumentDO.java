package com.example.zuoaiagent.knowledge.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import java.util.Date;

/**
 * 知识库文档实体
 */
@TableName("t_knowledge_document")
public class KnowledgeDocumentDO {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 所属知识库 ID */
    private Long kbId;

    /** 文档名称 */
    private String docName;

    /** 文件存储地址（MinIO 路径） */
    private String fileUrl;

    /** 文件类型：pdf / markdown / docx 等 */
    private String fileType;

    /** 文件大小（字节） */
    private Long fileSize;

    /** 来源类型：file */
    private String sourceType;

    /**
     * 文档状态：pending / success / failed
     */
    private String status;

    /** 是否启用：1-启用，0-禁用 */
    private Integer enabled;

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

    public KnowledgeDocumentDO() {
    }

    public KnowledgeDocumentDO(Long id, Long kbId, String docName, String fileUrl, String fileType,
                                Long fileSize, String sourceType, String status, Integer enabled,
                                String createdBy, String updatedBy, Date createTime, Date updateTime,
                                Integer deleted) {
        this.id = id;
        this.kbId = kbId;
        this.docName = docName;
        this.fileUrl = fileUrl;
        this.fileType = fileType;
        this.fileSize = fileSize;
        this.sourceType = sourceType;
        this.status = status;
        this.enabled = enabled;
        this.createdBy = createdBy;
        this.updatedBy = updatedBy;
        this.createTime = createTime;
        this.updateTime = updateTime;
        this.deleted = deleted;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getKbId() { return kbId; }
    public void setKbId(Long kbId) { this.kbId = kbId; }

    public String getDocName() { return docName; }
    public void setDocName(String docName) { this.docName = docName; }

    public String getFileUrl() { return fileUrl; }
    public void setFileUrl(String fileUrl) { this.fileUrl = fileUrl; }

    public String getFileType() { return fileType; }
    public void setFileType(String fileType) { this.fileType = fileType; }

    public Long getFileSize() { return fileSize; }
    public void setFileSize(Long fileSize) { this.fileSize = fileSize; }

    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Integer getEnabled() { return enabled; }
    public void setEnabled(Integer enabled) { this.enabled = enabled; }

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
        private Long kbId;
        private String docName;
        private String fileUrl;
        private String fileType;
        private Long fileSize;
        private String sourceType;
        private String status;
        private Integer enabled;
        private String createdBy;
        private String updatedBy;
        private Date createTime;
        private Date updateTime;
        private Integer deleted;

        public Builder id(Long id) { this.id = id; return this; }
        public Builder kbId(Long kbId) { this.kbId = kbId; return this; }
        public Builder docName(String docName) { this.docName = docName; return this; }
        public Builder fileUrl(String fileUrl) { this.fileUrl = fileUrl; return this; }
        public Builder fileType(String fileType) { this.fileType = fileType; return this; }
        public Builder fileSize(Long fileSize) { this.fileSize = fileSize; return this; }
        public Builder sourceType(String sourceType) { this.sourceType = sourceType; return this; }
        public Builder status(String status) { this.status = status; return this; }
        public Builder enabled(Integer enabled) { this.enabled = enabled; return this; }
        public Builder createdBy(String createdBy) { this.createdBy = createdBy; return this; }
        public Builder updatedBy(String updatedBy) { this.updatedBy = updatedBy; return this; }
        public Builder createTime(Date createTime) { this.createTime = createTime; return this; }
        public Builder updateTime(Date updateTime) { this.updateTime = updateTime; return this; }
        public Builder deleted(Integer deleted) { this.deleted = deleted; return this; }

        public KnowledgeDocumentDO build() {
            return new KnowledgeDocumentDO(id, kbId, docName, fileUrl, fileType, fileSize, sourceType,
                    status, enabled, createdBy, updatedBy, createTime, updateTime, deleted);
        }
    }

    @Override
    public String toString() {
        return "KnowledgeDocumentDO{id=" + id + ", kbId=" + kbId + ", docName='" + docName + "'}";
    }
}
