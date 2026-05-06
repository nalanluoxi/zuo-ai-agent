package com.example.zuoaiagent.knowledge.model.request;

import com.example.zuoaiagent.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class KnowledgeBasePageRequest extends PageRequest {

    /** 知识库名称（模糊搜索） */
    private String name;
}