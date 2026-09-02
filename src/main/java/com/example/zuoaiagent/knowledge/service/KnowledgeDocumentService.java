package com.example.zuoaiagent.knowledge.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.example.zuoaiagent.knowledge.model.request.KnowledgeDocumentPageRequest;
import com.example.zuoaiagent.knowledge.model.vo.KnowledgeDocumentVO;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

public interface KnowledgeDocumentService {

    /** 上传文档到 MinIO 并记录数据库（默认新增模式） */
    KnowledgeDocumentVO upload(Long kbId, MultipartFile file);

    /**
     * 上传文档（支持覆盖模式）
     *
     * @param kbId   知识库 ID
     * @param file   上传文件
     * @param docName 自定义文档名称（可为 null，使用原始文件名）
     * @param mode   模式：new（新增）或 overwrite（覆盖同名文档）
     */
    KnowledgeDocumentVO upload(Long kbId, MultipartFile file, String docName, String mode);

    /**
     * 检查知识库内是否存在同名文档
     *
     * @return 如果存在返回 { exists: true, docId, docName, contentMd5, fileSize }
     *         如果不存在返回 { exists: false }
     */
    Map<String, Object> checkDocName(Long kbId, String docName);

    /** 删除文档（同时删除 MinIO 文件） */
    void delete(Long docId);

    /** 查询文档详情 */
    KnowledgeDocumentVO getById(Long docId);

    /** 分页查询文档列表 */
    IPage<KnowledgeDocumentVO> page(Long kbId, KnowledgeDocumentPageRequest request);

    /** 重新入库文档（清理向量后重新触发 ETL） */
    void reIngest(Long docId);
}