package com.example.zuoaiagent.knowledge.service;

import cn.hutool.crypto.digest.DigestUtil;
import com.example.zuoaiagent.knowledge.entity.KnowledgeDocumentDO;
import com.example.zuoaiagent.knowledge.mapper.KnowledgeDocumentMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class KnowledgeDocumentPersistenceService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeDocumentPersistenceService.class);

    private final KnowledgeDocumentMapper documentMapper;
    private final JdbcTemplate jdbcTemplate;

    public KnowledgeDocumentPersistenceService(KnowledgeDocumentMapper documentMapper,
                                               JdbcTemplate jdbcTemplate) {
        this.documentMapper = documentMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(rollbackFor = Exception.class)
    public KnowledgeDocumentDO persist(Long kbId, byte[] fileBytes, String originalFilename,
                                        String fileType, String contentType) {
        String storageKey = java.util.UUID.randomUUID().toString().replace("-", "") + "_" + originalFilename;
        String contentMd5 = DigestUtil.md5Hex(fileBytes);

        jdbcTemplate.update(
                "INSERT INTO t_knowledge_document_file (storage_key, content, content_type) VALUES (?, ?, ?)",
                storageKey, fileBytes, contentType
        );

        KnowledgeDocumentDO documentDO = KnowledgeDocumentDO.builder()
                .kbId(kbId)
                .docName(originalFilename)
                .fileUrl(storageKey)
                .fileType(fileType)
                .fileSize((long) fileBytes.length)
                .sourceType("file")
                .status("pending")
                .contentMd5(contentMd5)
                .enabled(1)
                .createdBy("system")
                .updatedBy("system")
                .deleted(0)
                .build();

        try {
            documentMapper.insert(documentDO);
        } catch (DuplicateKeyException e) {
            log.warn("文档内容重复，contentMd5={}, kbId={}", contentMd5, kbId);
            throw e;
        }

        log.info("文档持久化完成: docId={}, storageKey={}, contentMd5={}", documentDO.getId(), storageKey, contentMd5);
        return documentDO;
    }
}