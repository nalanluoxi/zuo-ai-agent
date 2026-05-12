package com.example.zuoaiagent.knowledge.model.vo;

import java.util.Date;

public class KnowledgeDocumentVO {

    private Long id;

    private Long kbId;

    private String docName;

    private String fileUrl;

    private String fileType;

    private Long fileSize;

    private String sourceType;

    /** 状态：pending / success / failed */
    private String status;

    private Integer enabled;

    private String createdBy;

    private Date createTime;

    private Date updateTime;

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

    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date createTime) { this.createTime = createTime; }

    public Date getUpdateTime() { return updateTime; }
    public void setUpdateTime(Date updateTime) { this.updateTime = updateTime; }
}
