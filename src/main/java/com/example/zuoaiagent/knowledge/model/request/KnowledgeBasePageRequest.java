package com.example.zuoaiagent.knowledge.model.request;

import com.example.zuoaiagent.common.PageRequest;

public class KnowledgeBasePageRequest extends PageRequest {

    /** 知识库名称（模糊搜索） */
    private String name;

    /** 创建者 ID（过滤"我的知识库"） */
    private String createdBy;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
}
