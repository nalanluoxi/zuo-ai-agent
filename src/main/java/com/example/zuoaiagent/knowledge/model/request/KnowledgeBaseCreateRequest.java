package com.example.zuoaiagent.knowledge.model.request;

import jakarta.validation.constraints.NotBlank;

public class KnowledgeBaseCreateRequest {

    /** 知识库名称 */
    @NotBlank(message = "知识库名称不能为空")
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

    /** 绑定的意图节点 ID 列表 */
    private java.util.List<Long> intentNodeIds;

    public java.util.List<Long> getIntentNodeIds() { return intentNodeIds; }
    public void setIntentNodeIds(java.util.List<Long> intentNodeIds) { this.intentNodeIds = intentNodeIds; }
}
