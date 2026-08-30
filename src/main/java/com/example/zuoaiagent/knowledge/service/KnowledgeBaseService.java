package com.example.zuoaiagent.knowledge.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.example.zuoaiagent.knowledge.model.request.KnowledgeBaseCreateRequest;
import com.example.zuoaiagent.knowledge.model.request.KnowledgeBasePageRequest;
import com.example.zuoaiagent.knowledge.model.request.KnowledgeBaseUpdateRequest;
import com.example.zuoaiagent.knowledge.model.vo.KnowledgeBaseVO;

import java.util.List;
import java.util.Map;

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

    void disable(Long id);
    void enable(Long id);
    IPage<KnowledgeBaseVO> search(String keyword, KnowledgeBasePageRequest request);
    
    /** 修改知识库可见状态（PUBLIC/PRIVATE） */
    void updateVisibility(Long id, String visibility);

    // ==================== P23 新增：Owner 和可读性管理 ====================

    /** P23：添加知识库 Owner */
    void addOwner(Long knowledgeBaseId, String ownerId);

    /** P23：移除知识库 Owner */
    void removeOwner(Long knowledgeBaseId, String ownerId);

    /** P23：获取知识库所有 Owner 列表 */
    List<Map<String, Object>> getOwners(Long knowledgeBaseId);

    /** P23：更新知识库可读性（PRIVATE/TEAM/PUBLIC） */
    void updateReadability(Long knowledgeBaseId, String readability);
}