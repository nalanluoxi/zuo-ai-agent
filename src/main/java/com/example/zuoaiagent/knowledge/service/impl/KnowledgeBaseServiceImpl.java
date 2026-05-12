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
import com.example.zuoaiagent.knowledge.mapper.KnowledgeBaseMapper;
import com.example.zuoaiagent.knowledge.mapper.KnowledgeDocumentMapper;
import com.example.zuoaiagent.knowledge.model.request.KnowledgeBaseCreateRequest;
import com.example.zuoaiagent.knowledge.model.request.KnowledgeBasePageRequest;
import com.example.zuoaiagent.knowledge.model.request.KnowledgeBaseUpdateRequest;
import com.example.zuoaiagent.knowledge.model.vo.KnowledgeBaseVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class KnowledgeBaseServiceImpl implements com.example.zuoaiagent.knowledge.service.KnowledgeBaseService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeBaseServiceImpl.class);

    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final KnowledgeDocumentMapper knowledgeDocumentMapper;

    public KnowledgeBaseServiceImpl(KnowledgeBaseMapper knowledgeBaseMapper,
                                    KnowledgeDocumentMapper knowledgeDocumentMapper) {
        this.knowledgeBaseMapper = knowledgeBaseMapper;
        this.knowledgeDocumentMapper = knowledgeDocumentMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(KnowledgeBaseCreateRequest request) {
        String name = request.getName().trim();

        Long count = knowledgeBaseMapper.selectCount(
                Wrappers.lambdaQuery(KnowledgeBaseDO.class)
                        .eq(KnowledgeBaseDO::getName, name)
                        .eq(KnowledgeBaseDO::getDeleted, 0)
        );
        if (count > 0) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "知识库名称已存在：" + name);
        }

        KnowledgeBaseDO kbDO = KnowledgeBaseDO.builder()
                .name(name)
                .description(request.getDescription())
                .createdBy("system")
                .updatedBy("system")
                .deleted(0)
                .build();
        knowledgeBaseMapper.insert(kbDO);
        return kbDO.getId();
    }

    @Override
    public void update(Long id, KnowledgeBaseUpdateRequest request) {
        KnowledgeBaseDO kbDO = knowledgeBaseMapper.selectById(id);
        if (kbDO == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "知识库不存在");
        }

        if (StringUtils.hasText(request.getName())) {
            String newName = request.getName().trim();
            Long count = knowledgeBaseMapper.selectCount(
                    Wrappers.lambdaQuery(KnowledgeBaseDO.class)
                            .eq(KnowledgeBaseDO::getName, newName)
                            .ne(KnowledgeBaseDO::getId, id)
                            .eq(KnowledgeBaseDO::getDeleted, 0)
            );
            if (count > 0) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "知识库名称已存在：" + newName);
            }
            kbDO.setName(newName);
        }

        if (request.getDescription() != null) {
            kbDO.setDescription(request.getDescription());
        }

        kbDO.setUpdatedBy("system");
        knowledgeBaseMapper.updateById(kbDO);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        KnowledgeBaseDO kbDO = knowledgeBaseMapper.selectById(id);
        if (kbDO == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "知识库不存在");
        }

        Long docCount = knowledgeDocumentMapper.selectCount(
                Wrappers.lambdaQuery(KnowledgeDocumentDO.class)
                        .eq(KnowledgeDocumentDO::getKbId, id)
                        .eq(KnowledgeDocumentDO::getDeleted, 0)
        );
        if (docCount != null && docCount > 0) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "当前知识库下还有文档，请先删除文档");
        }

        kbDO.setDeleted(1);
        kbDO.setUpdatedBy("system");
        knowledgeBaseMapper.deleteById(kbDO);
    }

    @Override
    public KnowledgeBaseVO getById(Long id) {
        KnowledgeBaseDO kbDO = knowledgeBaseMapper.selectById(id);
        if (kbDO == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "知识库不存在");
        }
        return BeanUtil.toBean(kbDO, KnowledgeBaseVO.class);
    }

    @Override
    public IPage<KnowledgeBaseVO> page(KnowledgeBasePageRequest request) {
        LambdaQueryWrapper<KnowledgeBaseDO> queryWrapper = Wrappers.lambdaQuery(KnowledgeBaseDO.class)
                .like(StringUtils.hasText(request.getName()), KnowledgeBaseDO::getName, request.getName())
                .eq(KnowledgeBaseDO::getDeleted, 0)
                .orderByDesc(KnowledgeBaseDO::getUpdateTime);

        Page<KnowledgeBaseDO> page = new Page<>(request.getCurrent(), request.getPageSize());
        IPage<KnowledgeBaseDO> result = knowledgeBaseMapper.selectPage(page, queryWrapper);

        Map<Long, Long> docCountMap = Map.of();
        List<Long> kbIds = result.getRecords().stream()
                .map(KnowledgeBaseDO::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        if (!kbIds.isEmpty()) {
            List<Map<String, Object>> rows = knowledgeDocumentMapper.selectMaps(
                    Wrappers.query(KnowledgeDocumentDO.class)
                            .select("kb_id AS kbId", "COUNT(1) AS docCount")
                            .in("kb_id", kbIds)
                            .eq("deleted", 0)
                            .groupBy("kb_id")
            );
            docCountMap = rows.stream()
                    .filter(r -> r.get("kbId") != null)
                    .collect(Collectors.toMap(
                            r -> Long.parseLong(r.get("kbId").toString()),
                            r -> r.get("docCount") instanceof Number n ? n.longValue() : 0L
                    ));
        }

        final Map<Long, Long> finalDocCountMap = docCountMap;
        return result.convert(each -> {
            KnowledgeBaseVO vo = BeanUtil.toBean(each, KnowledgeBaseVO.class);
            vo.setDocumentCount(finalDocCountMap.getOrDefault(each.getId(), 0L));
            return vo;
        });
    }
}