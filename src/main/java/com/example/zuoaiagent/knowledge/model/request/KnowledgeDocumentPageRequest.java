package com.example.zuoaiagent.knowledge.model.request;

import com.example.zuoaiagent.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class KnowledgeDocumentPageRequest extends PageRequest {

    /** 文档名称（模糊搜索） */
    private String keyword;

    /** 文档状态过滤 */
    private String status;
}