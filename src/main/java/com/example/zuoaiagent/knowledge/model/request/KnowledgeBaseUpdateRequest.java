package com.example.zuoaiagent.knowledge.model.request;

import lombok.Data;

@Data
public class KnowledgeBaseUpdateRequest {

    /** 知识库名称 */
    private String name;

    /** 描述 */
    private String description;
}