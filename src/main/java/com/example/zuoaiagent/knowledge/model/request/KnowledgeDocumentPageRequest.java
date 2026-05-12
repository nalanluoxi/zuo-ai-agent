package com.example.zuoaiagent.knowledge.model.request;

import com.example.zuoaiagent.common.PageRequest;

public class KnowledgeDocumentPageRequest extends PageRequest {

    /** 文档名称（模糊搜索） */
    private String keyword;

    /** 文档状态过滤 */
    private String status;

    public String getKeyword() { return keyword; }
    public void setKeyword(String keyword) { this.keyword = keyword; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
