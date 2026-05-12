package com.example.zuoaiagent.knowledge.model.request;

import lombok.Data;

@Data
public class KnowledgeBaseUpdateRequest {

    private Long id;
    /** 知识库名称 */
    private String name;

    /** 描述 */
    private String description;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
