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

        KnowledgeDocumentDO existing = checkDuplicateInKnowledgeBase(kbId, contentMd5);
        if (existing != null) {
            log.info("文档已存在(同一知识库内MD5重复): docId={}, contentMd5={}", existing.getId(), contentMd5);
            return BeanUtil.toBean(existing, KnowledgeDocumentVO.class);
        }

        KnowledgeDocumentDO documentDO = persistenceService.persist(
                kbId, fileBytes, originalFilename, fileType, file.getContentType());

        Map<String, Object> message = new HashMap<>();
        message.put("docId", documentDO.getId());
        message.put("kbId", kbId);
        rabbitTemplate.convertAndSend(ingestionQueue, message);
        log.info("文档入库消息已发送: docId={}, queue={}", documentDO.getId(), ingestionQueue);

        return BeanUtil.toBean(documentDO, KnowledgeDocumentVO.class);
    }

    private KnowledgeDocumentDO checkDuplicateInKnowledgeBase(Long kbId, String contentMd5) {
        if (!StringUtils.hasText(contentMd5)) {
            return null;
        }
        LambdaQueryWrapper<KnowledgeDocumentDO> wrapper = Wrappers.lambdaQuery(KnowledgeDocumentDO.class)
                .eq(KnowledgeDocumentDO::getKbId, kbId)
                .eq(KnowledgeDocumentDO::getContentMd5, contentMd5)
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