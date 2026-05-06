package com.example.zuoaiagent.knowledge.model.vo;

import lombok.Data;

import java.util.Date;

@Data
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
}