package com.example.zuoaiagent.knowledge.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.example.zuoaiagent.knowledge.model.request.KnowledgeBaseCreateRequest;
import com.example.zuoaiagent.knowledge.model.request.KnowledgeBasePageRequest;
import com.example.zuoaiagent.knowledge.model.request.KnowledgeBaseUpdateRequest;
import com.example.zuoaiagent.knowledge.model.vo.KnowledgeBaseVO;

public interface KnowledgeBaseService {

    /** 创建知识库，返回知识库ID */
    Long create(KnowledgeBaseCreateRequest request);

    /** 修改知识库名称/描述 */
    void update(Long id, KnowledgeBaseUpdateRequest request);

    /** 删除知识库（有文档时拒绝删除） */
    void delete(Long id);

    /** 查询知识库详情 */
    KnowledgeBaseVO getById(Long id);

    /** 分页查询知识库列表 */
    IPage<KnowledgeBaseVO> page(KnowledgeBasePageRequest request);
}