package com.example.zuoaiagent.knowledge.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.zuoaiagent.exception.BusinessException;
import com.example.zuoaiagent.exception.ErrorCode;
import com.example.zuoaiagent.knowledge.entity.KnowledgeBaseDO;
import com.example.zuoaiagent.knowledge.entity.KnowledgeDocumentDO;
import com.example.zuoaiagent.knowledge.ingestion.DocumentIngestionService;
import com.example.zuoaiagent.knowledge.mapper.KnowledgeBaseMapper;
import com.example.zuoaiagent.knowledge.mapper.KnowledgeDocumentMapper;
import com.example.zuoaiagent.knowledge.model.request.KnowledgeDocumentPageRequest;
import com.example.zuoaiagent.knowledge.model.vo.KnowledgeDocumentVO;
import com.example.zuoaiagent.knowledge.service.KnowledgeDocumentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Service
public class KnowledgeDocumentServiceImpl implements KnowledgeDocumentService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeDocumentServiceImpl.class);

    private final KnowledgeDocumentMapper documentMapper;
    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final JdbcTemplate jdbcTemplate;
    /** 文档入库服务：异步执行分块 + 向量化 + 写入 PgVector */
    private final DocumentIngestionService ingestionService;

    public KnowledgeDocumentServiceImpl(KnowledgeDocumentMapper documentMapper,
                                        KnowledgeBaseMapper knowledgeBaseMapper,
                                        JdbcTemplate jdbcTemplate,
                                        DocumentIngestionService ingestionService) {
        this.documentMapper = documentMapper;
        this.knowledgeBaseMapper = knowledgeBaseMapper;
        this.jdbcTemplate = jdbcTemplate;
        this.ingestionService = ingestionService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeDocumentVO upload(Long kbId, MultipartFile file) {
        KnowledgeBaseDO kbDO = knowledgeBaseMapper.selectById(kbId);
        if (kbDO == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "知识库不存在");
        }
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "上传文件不能为空");
        }

        String originalFilename = file.getOriginalFilename();
        String fileType = detectFileType(originalFilename);
        String storageKey = UUID.randomUUID().toString().replace("-", "") + "_" + originalFilename;

        // 将文件内容存入 Supabase（t_knowledge_document_file 表的 content 字段）
        try {
            byte[] bytes = file.getBytes();
            jdbcTemplate.update(
                    "INSERT INTO t_knowledge_document_file (storage_key, content, content_type) VALUES (?, ?, ?)",
                    storageKey, bytes, file.getContentType()
            );
            log.info("文件上传 Supabase 成功: storageKey={}", storageKey);
        } catch (Exception e) {
            log.error("文件上传 Supabase 失败: storageKey={}", storageKey, e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "文件上传失败：" + e.getMessage());
        }

        KnowledgeDocumentDO documentDO = KnowledgeDocumentDO.builder()
                .kbId(kbId)
                .docName(originalFilename)
                .fileUrl(storageKey)
                .fileType(fileType)
                .fileSize(file.getSize())
                .sourceType("file")
                .status("pending")
                .enabled(1)
                .createdBy("system")
                .updatedBy("system")
                .deleted(0)
                .build();
        documentMapper.insert(documentDO);

        // 异步触发分块 + 向量化流水线（@Async，不阻塞上传接口响应）
        // 文档状态：pending → success（成功）或 failed（异常）
        Long docId = documentDO.getId();
        log.info("文档记录已保存: docId={}, 触发异步入库流水线", docId);
        ingestionService.ingest(docId);

        return BeanUtil.toBean(documentDO, KnowledgeDocumentVO.class);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long docId) {
        KnowledgeDocumentDO documentDO = documentMapper.selectById(docId);
        if (documentDO == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "文档不存在");
        }

        // 删除 Supabase 中存储的文件内容
        if (StringUtils.hasText(documentDO.getFileUrl())) {
            try {
                jdbcTemplate.update(
                        "DELETE FROM t_knowledge_document_file WHERE storage_key = ?",
                        documentDO.getFileUrl()
                );
                log.info("Supabase 文件删除成功: storageKey={}", documentDO.getFileUrl());
            } catch (Exception e) {
                log.warn("Supabase 文件删除失败: storageKey={}", documentDO.getFileUrl(), e);
            }
        }

        documentDO.setDeleted(1);
        documentDO.setUpdatedBy("system");
        documentMapper.deleteById(documentDO);
    }

    @Override
    public KnowledgeDocumentVO getById(Long docId) {
        KnowledgeDocumentDO documentDO = documentMapper.selectById(docId);
        if (documentDO == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "文档不存在");
        }
        return BeanUtil.toBean(documentDO, KnowledgeDocumentVO.class);
    }

    @Override
    public IPage<KnowledgeDocumentVO> page(Long kbId, KnowledgeDocumentPageRequest request) {
        LambdaQueryWrapper<KnowledgeDocumentDO> queryWrapper = Wrappers.lambdaQuery(KnowledgeDocumentDO.class)
                .eq(KnowledgeDocumentDO::getKbId, kbId)
                .eq(KnowledgeDocumentDO::getDeleted, 0)
                .like(StringUtils.hasText(request.getKeyword()), KnowledgeDocumentDO::getDocName, request.getKeyword())
                .eq(StringUtils.hasText(request.getStatus()), KnowledgeDocumentDO::getStatus, request.getStatus())
                .orderByDesc(KnowledgeDocumentDO::getCreateTime);

        Page<KnowledgeDocumentDO> page = new Page<>(request.getCurrent(), request.getPageSize());
        return documentMapper.selectPage(page, queryWrapper)
                .convert(each -> BeanUtil.toBean(each, KnowledgeDocumentVO.class));
    }

    private String detectFileType(String filename) {
        if (!StringUtils.hasText(filename)) {
            return "unknown";
        }
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot + 1).toLowerCase() : "unknown";
    }
}