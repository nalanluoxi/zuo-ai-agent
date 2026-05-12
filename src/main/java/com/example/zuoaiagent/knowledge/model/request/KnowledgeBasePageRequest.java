package com.example.zuoaiagent.knowledge.model.request;

import com.example.zuoaiagent.common.PageRequest;

public class KnowledgeBasePageRequest extends PageRequest {

    /** 知识库名称（模糊搜索） */
    private String name;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}
