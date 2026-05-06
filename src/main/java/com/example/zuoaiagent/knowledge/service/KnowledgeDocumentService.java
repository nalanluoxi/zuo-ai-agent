package com.example.zuoaiagent.knowledge.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.example.zuoaiagent.knowledge.model.request.KnowledgeDocumentPageRequest;
import com.example.zuoaiagent.knowledge.model.vo.KnowledgeDocumentVO;
import org.springframework.web.multipart.MultipartFile;

public interface KnowledgeDocumentService {

    /** 上传文档到 MinIO 并记录数据库 */
    KnowledgeDocumentVO upload(Long kbId, MultipartFile file);

    /** 删除文档（同时删除 MinIO 文件） */
    void delete(Long docId);

    /** 查询文档详情 */
    KnowledgeDocumentVO getById(Long docId);

    /** 分页查询文档列表 */
    IPage<KnowledgeDocumentVO> page(Long kbId, KnowledgeDocumentPageRequest request);
}