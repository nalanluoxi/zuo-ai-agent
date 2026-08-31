package com.example.zuoaiagent.knowledge.model.request;

import lombok.Data;

@Data
public class KnowledgeBaseUpdateRequest {

    private Long id;
    /** 知识库名称 */
    private String name;

    /** 描述 */
    private String description;

    /** 可读性：private/team/public */
    private String readability;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getReadability() { return readability; }
    public void setReadability(String readability) { this.readability = readability; }
}
