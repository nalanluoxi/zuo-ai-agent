package com.example.zuoaiagent.knowledge.model.request;

import jakarta.validation.constraints.NotBlank;

public class KnowledgeBaseCreateRequest {

    /** 知识库名称 */
    @NotBlank(message = "知识库名称不能为空")
    private String name;

    /** 描述 */
    private String description;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
