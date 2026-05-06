package com.example.zuoaiagent.knowledge.model.vo;

import lombok.Data;

import java.util.Date;

@Data
public class KnowledgeBaseVO {

    private Long id;

    private String name;

    private String description;

    /** 文档数量 */
    private Long documentCount;

    private String createdBy;

    private Date createTime;

    private Date updateTime;
}