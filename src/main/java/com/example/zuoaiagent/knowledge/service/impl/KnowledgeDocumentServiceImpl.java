package com.example.zuoaiagent.knowledge.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.crypto.digest.DigestUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.zuoaiagent.exception.BusinessException;
import com.example.zuoaiagent.exception.ErrorCode;
import com.example.zuoaiagent.knowledge.entity.KnowledgeBaseDO;
import com.example.zuoaiagent.knowledge.entity.KnowledgeDocumentDO;
import com.example.zuoaiagent.knowledge.mapper.KnowledgeBaseMapper;
import com.example.zuoaiagent.knowledge.mapper.KnowledgeDocumentMapper;
import com.example.zuoaiagent.knowledge.model.request.KnowledgeDocumentPageRequest;
import com.example.zuoaiagent.knowledge.model.vo.KnowledgeDocumentVO;
import com.example.zuoaiagent.knowledge.service.KnowledgeDocumentPersistenceService;
import com.example.zuoaiagent.knowledge.service.KnowledgeDocumentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

@Service
public class KnowledgeDocumentServiceImpl implements KnowledgeDocumentService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeDocumentServiceImpl.class);

    private final KnowledgeDocumentMapper documentMapper;
    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final JdbcTemplate jdbcTemplate;
    private final KnowledgeDocumentPersistenceService persistenceService;
    private final RabbitTemplate rabbitTemplate;

    @Value("${zuo.rabbitmq.queues.ingestion:ingestion.queue}")
    private String ingestionQueue;

    public KnowledgeDocumentServiceImpl(KnowledgeDocumentMapper documentMapper,
                                        KnowledgeBaseMapper knowledgeBaseMapper,
                                        JdbcTemplate jdbcTemplate,
                                        KnowledgeDocumentPersistenceService persistenceService,
                                        RabbitTemplate rabbitTemplate) {
        this.documentMapper = documentMapper;
        this.knowledgeBaseMapper = knowledgeBaseMapper;
        this.jdbcTemplate = jdbcTemplate;
        this.persistenceService = persistenceService;
        this.rabbitTemplate = rabbitTemplate;
    }

    @Override
    public KnowledgeDocumentVO upload(Long kbId, MultipartFile file) {
        return upload(kbId, file, null, "new");
    }

    @Override
    public KnowledgeDocumentVO upload(Long kbId, MultipartFile file, String docName, String mode) {
        KnowledgeBaseDO kbDO = knowledgeBaseMapper.selectById(kbId);
        if (kbDO == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "知识库不存在");
        }
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "上传文件不能为空");
        }

        String originalFilename = file.getOriginalFilename();
        String fileType = detectFileType(originalFilename);

        byte[] fileBytes;
        try {
            fileBytes = file.getBytes();
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "文件读取失败");
        }

        String contentMd5 = DigestUtil.md5Hex(fileBytes);

        // 覆盖模式：先清理同名文档的旧数据
        if ("overwrite".equalsIgnoreCase(mode) && StringUtils.hasText(docName)) {
            KnowledgeDocumentDO existing = findByNameInKnowledgeBase(kbId, docName);
            if (existing != null) {
                log.info("覆盖模式：清理旧文档 docId={}, docName={}", existing.getId(), docName);
                // 清理向量
                try {
                    jdbcTemplate.update("DELETE FROM vector_store WHERE doc_id = ?", String.valueOf(existing.getId()));
                } catch (Exception e) {
                    log.warn("向量数据清理失败: docId={}", existing.getId(), e);
                }
                // 清理文件字节
                if (StringUtils.hasText(existing.getFileUrl())) {
                    try {
                        jdbcTemplate.update("DELETE FROM t_knowledge_document_file WHERE storage_key = ?", existing.getFileUrl());
                    } catch (Exception e) {
                        log.warn("文件删除失败: storageKey={}", existing.getFileUrl(), e);
                    }
                }
                // 逻辑删除旧文档（使用 jdbcTemplate 绕过 @TableLogic 拦截）
                jdbcTemplate.update("UPDATE t_knowledge_document SET deleted = 1, updated_by = 'system', update_time = NOW() WHERE id = ?", existing.getId());
            }
        }

        // 使用自定义文件名或原始文件名
        String finalDocName = (StringUtils.hasText(docName)) ? docName : originalFilename;

        KnowledgeDocumentDO documentDO = persistenceService.persist(
                kbId, fileBytes, finalDocName, fileType, file.getContentType());

        Map<String, Object> message = new HashMap<>();
        message.put("docId", documentDO.getId());
        message.put("kbId", kbId);
        rabbitTemplate.convertAndSend(ingestionQueue, message);
        log.info("文档入库消息已发送: docId={}, docName={}, queue={}", documentDO.getId(), finalDocName, ingestionQueue);

        return BeanUtil.toBean(documentDO, KnowledgeDocumentVO.class);
    }

    @Override
    public Map<String, Object> checkDocName(Long kbId, String docName) {
        Map<String, Object> result = new HashMap<>();
        if (!StringUtils.hasText(docName)) {
            result.put("exists", false);
            return result;
        }

        KnowledgeDocumentDO existing = findByNameInKnowledgeBase(kbId, docName);
        if (existing != null) {
            result.put("exists", true);
            result.put("docId", existing.getId());
            result.put("docName", existing.getDocName());
            result.put("contentMd5", existing.getContentMd5());
            result.put("fileSize", existing.getFileSize());
            result.put("status", existing.getStatus());
        } else {
            result.put("exists", false);
        }
        return result;
    }

    private KnowledgeDocumentDO findByNameInKnowledgeBase(Long kbId, String docName) {
        if (!StringUtils.hasText(docName)) {
            return null;
        }
        LambdaQueryWrapper<KnowledgeDocumentDO> wrapper = Wrappers.lambdaQuery(KnowledgeDocumentDO.class)
                .eq(KnowledgeDocumentDO::getKbId, kbId)
                .eq(KnowledgeDocumentDO::getDocName, docName)
                .eq(KnowledgeDocumentDO::getDeleted, 0);
        return documentMapper.selectOne(wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long docId) {
        KnowledgeDocumentDO documentDO = documentMapper.selectById(docId);
        if (documentDO == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "文档不存在");
        }

        // 1. 清理 PgVector 中的向量数据
        try {
            int deletedVectors = jdbcTemplate.update(
                    "DELETE FROM vector_store WHERE doc_id = ?",
                    String.valueOf(docId)
            );
            log.info("向量数据已清理: docId={}, deletedCount={}", docId, deletedVectors);
        } catch (Exception e) {
            log.warn("向量数据清理失败: docId={}", docId, e);
        }

        // 2. 清理文件字节数据
        if (StringUtils.hasText(documentDO.getFileUrl())) {
            try {
                jdbcTemplate.update(
                        "DELETE FROM t_knowledge_document_file WHERE storage_key = ?",
                        documentDO.getFileUrl()
                );
            } catch (Exception e) {
                log.warn("文件删除失败: storageKey={}", documentDO.getFileUrl(), e);
            }
        }

        // 3. 逻辑删除文档记录
        documentDO.setDeleted(1);
        documentDO.setUpdatedBy("system");
        documentMapper.deleteById(documentDO);

        log.info("文档已删除: docId={}", docId);
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

    @Override
    public void reIngest(Long docId) {
        KnowledgeDocumentDO documentDO = documentMapper.selectById(docId);
        if (documentDO == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "文档不存在");
        }

        // 1. 清理 PgVector 中的向量数据
        try {
            int deletedVectors = jdbcTemplate.update(
                    "DELETE FROM vector_store WHERE doc_id = ?",
                    String.valueOf(docId)
            );
            log.info("向量数据已清理: docId={}, deletedCount={}", docId, deletedVectors);
        } catch (Exception e) {
            log.warn("向量数据清理失败: docId={}", docId, e);
        }

        // 2. 重置文档状态为待处理
        documentDO.setStatus("pending");
        documentDO.setUpdatedBy("system");
        documentMapper.updateById(documentDO);

        // 3. 发送消息到 RabbitMQ 重新触发 ETL
        Map<String, Object> message = new HashMap<>();
        message.put("docId", documentDO.getId());
        message.put("kbId", documentDO.getKbId());
        rabbitTemplate.convertAndSend(ingestionQueue, message);
        log.info("文档重新入库消息已发送: docId={}, queue={}", documentDO.getId(), ingestionQueue);
    }

    private String detectFileType(String filename) {
        if (!StringUtils.hasText(filename)) {
            return "unknown";
        }
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot + 1).toLowerCase() : "unknown";
    }
}