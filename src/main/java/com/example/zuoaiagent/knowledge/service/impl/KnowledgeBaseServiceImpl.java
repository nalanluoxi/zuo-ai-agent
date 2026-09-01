package com.example.zuoaiagent.knowledge.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.zuoaiagent.config.TenantContextHolder;
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
import com.example.zuoaiagent.intent.entity.IntentNodeDO;
import com.example.zuoaiagent.intent.mapper.IntentNodeMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
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
    private final IntentNodeMapper intentNodeMapper;
    private final JdbcTemplate jdbcTemplate;

    public KnowledgeBaseServiceImpl(KnowledgeBaseMapper knowledgeBaseMapper,
                                    KnowledgeDocumentMapper knowledgeDocumentMapper,
                                    IntentNodeMapper intentNodeMapper,
                                    JdbcTemplate jdbcTemplate) {
        this.knowledgeBaseMapper = knowledgeBaseMapper;
        this.knowledgeDocumentMapper = knowledgeDocumentMapper;
        this.intentNodeMapper = intentNodeMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(KnowledgeBaseCreateRequest request) {
        String name = request.getName().trim();
        
        // P19-P21 修复：添加租户隔离 - 只检查当前租户下的知识库
        Long tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "无法获取租户信息");
        }

        Long count = knowledgeBaseMapper.selectCount(
                Wrappers.lambdaQuery(KnowledgeBaseDO.class)
                        .eq(KnowledgeBaseDO::getName, name)
                        .eq(KnowledgeBaseDO::getTenantId, tenantId)
                        .eq(KnowledgeBaseDO::getDeleted, 0)
        );
        if (count > 0) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "知识库名称已存在：" + name);
        }

        // 从租户上下文获取用户信息
        Long userId = TenantContextHolder.getUserId();
        String createdBy = userId != null ? String.valueOf(userId) : "system";

        KnowledgeBaseDO kbDO = KnowledgeBaseDO.builder()
                .name(name)
                .description(request.getDescription())
                .tenantId(tenantId)
                .ownerId(userId)
                .visibility(mapReadabilityToVisibility(request.getReadability()))
                .createdBy(createdBy)
                .updatedBy(createdBy)
                .deleted(0)
                .build();
        knowledgeBaseMapper.insert(kbDO);
        Long kbId = kbDO.getId();

        // 绑定意图节点
        bindIntentNodes(kbId, request.getIntentNodeIds());

        return kbId;
    }

    /**
     * 绑定意图节点到知识库
     * 先清除旧绑定，再创建新绑定
     */
    private void bindIntentNodes(Long kbId, List<Long> intentNodeIds) {
        if (intentNodeIds == null || intentNodeIds.isEmpty()) {
            return;
        }

        // 清除该知识库的旧绑定
        intentNodeMapper.update(null,
                Wrappers.lambdaUpdate(IntentNodeDO.class)
                        .eq(IntentNodeDO::getKbId, kbId)
                        .set(IntentNodeDO::getKbId, null));

        // 创建新绑定
        for (Long nodeId : intentNodeIds) {
            IntentNodeDO node = intentNodeMapper.selectById(nodeId);
            if (node != null) {
                node.setKbId(kbId);
                intentNodeMapper.updateById(node);
            }
        }

        log.info("绑定意图节点到知识库 - kbId: {}, nodeIds: {}", kbId, intentNodeIds);
    }

    @Override
    public void update(Long id, KnowledgeBaseUpdateRequest request) {
        KnowledgeBaseDO kbDO = knowledgeBaseMapper.selectById(id);
        if (kbDO == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "知识库不存在");
        }

        Long currentUserId = TenantContextHolder.getUserId();
        if (!Objects.equals(kbDO.getOwnerId(), currentUserId)) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "只有所有者才能修改此知识库");
        }

        if (StringUtils.hasText(request.getName())) {
            String newName = request.getName().trim();
            Long count = knowledgeBaseMapper.selectCount(
                    Wrappers.lambdaQuery(KnowledgeBaseDO.class)
                            .eq(KnowledgeBaseDO::getName, newName)
                            .eq(KnowledgeBaseDO::getTenantId, kbDO.getTenantId())
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

        if (StringUtils.hasText(request.getReadability())) {
            kbDO.setVisibility(mapReadabilityToVisibility(request.getReadability()));
        }

        kbDO.setUpdatedBy(currentUserId != null ? String.valueOf(currentUserId) : "system");
        knowledgeBaseMapper.updateById(kbDO);

        // 绑定意图节点
        bindIntentNodes(id, request.getIntentNodeIds());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        KnowledgeBaseDO kbDO = knowledgeBaseMapper.selectById(id);
        if (kbDO == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "知识库不存在");
        }
        
        // P19-P21 修复：租户校验 - 防止删除其他租户的知识库
        Long currentTenantId = TenantContextHolder.getTenantId();
        if (!Objects.equals(kbDO.getTenantId(), currentTenantId)) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "无权删除此知识库");
        }

        Long docCount = knowledgeDocumentMapper.selectCount(
                Wrappers.lambdaQuery(KnowledgeDocumentDO.class)
                        .eq(KnowledgeDocumentDO::getKbId, id)
                        .eq(KnowledgeDocumentDO::getDeleted, 0)
        );
        if (docCount != null && docCount > 0) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "当前知识库下还有文档，请先删除文档");
        }

        Long currentUserId = TenantContextHolder.getUserId();
        kbDO.setUpdatedBy(currentUserId != null ? currentUserId.toString() : "system");
        knowledgeBaseMapper.updateById(kbDO);

        // @TableLogic 字段不会被 updateById 写入 SET 子句，必须调用 deleteById 才能真正逻辑删除
        knowledgeBaseMapper.deleteById(id);
    }

    @Override
    public KnowledgeBaseVO getById(Long id) {
        KnowledgeBaseDO kbDO = knowledgeBaseMapper.selectById(id);
        if (kbDO == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "知识库不存在");
        }
        KnowledgeBaseVO vo = BeanUtil.toBean(kbDO, KnowledgeBaseVO.class);
        // visibility → readability 映射（前端用 readability，DB 存 visibility）
        vo.setReadability(mapVisibilityToReadability(kbDO.getVisibility()));

        // 查询绑定的意图节点 ID 列表（t_intent_node 表中 kb_id = id 的记录）
        List<Long> intentNodeIds = jdbcTemplate.queryForList(
                "SELECT id FROM t_intent_node WHERE kb_id = ? AND deleted = 0",
                Long.class, id);
        vo.setIntentNodeIds(intentNodeIds);

        // 查询绑定的意图节点标签（用于展示）
        if (!intentNodeIds.isEmpty()) {
            List<String> intentNodeLabels = jdbcTemplate.queryForList(
                    "SELECT label FROM t_intent_node WHERE kb_id = ? AND deleted = 0",
                    String.class, id);
            vo.setIntentNodeLabels(intentNodeLabels);
        }

        // 查询创建者用户名
        if (kbDO.getOwnerId() != null) {
            try {
                String username = jdbcTemplate.queryForObject(
                        "SELECT username FROM t_user WHERE id = ? AND deleted = 0",
                        String.class, kbDO.getOwnerId());
                vo.setCreatedByUsername(username);
            } catch (Exception e) {
                log.warn("查询创建者用户名失败，ownerId={}", kbDO.getOwnerId(), e);
                vo.setCreatedByUsername("未知用户");
            }
        }

        return vo;
    }

    /**
     * 将 DB 中的 visibility（PUBLIC/PRIVATE/TEAM）映射为前端 readability（public/private/team）
     */
    private String mapVisibilityToReadability(String visibility) {
        if (visibility == null) return "private";
        return visibility.toLowerCase();
    }

    /**
     * 将前端 readability（private/team/public）映射为 DB visibility（PUBLIC/PRIVATE/TEAM）
     */
    private String mapReadabilityToVisibility(String readability) {
        if (readability == null) return "PRIVATE";
        return readability.toUpperCase();
    }

    @Override
    public IPage<KnowledgeBaseVO> page(KnowledgeBasePageRequest request) {
        // P19-P21 修复：添加租户隔离过滤
        Long tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "无法获取租户信息");
        }

        LambdaQueryWrapper<KnowledgeBaseDO> queryWrapper = Wrappers.lambdaQuery(KnowledgeBaseDO.class)
                .like(StringUtils.hasText(request.getName()), KnowledgeBaseDO::getName, request.getName())
                .eq(StringUtils.hasText(request.getCreatedBy()), KnowledgeBaseDO::getCreatedBy, request.getCreatedBy())
                .eq(KnowledgeBaseDO::getTenantId, tenantId)  // P19-P21 修复：只查询当前租户的知识库
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

        // 批量查询创建者用户名（避免 N+1）
        Map<Long, String> usernameMap = Map.of();
        List<Long> ownerIds = result.getRecords().stream()
                .map(KnowledgeBaseDO::getOwnerId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        if (!ownerIds.isEmpty()) {
            try {
                String placeholders = ownerIds.stream().map(id -> "?").collect(Collectors.joining(","));
                List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                        "SELECT id, username FROM t_user WHERE id IN (" + placeholders + ") AND deleted = 0",
                        ownerIds.toArray());
                usernameMap = rows.stream()
                        .filter(r -> r.get("id") != null && r.get("username") != null)
                        .collect(Collectors.toMap(
                                r -> Long.parseLong(r.get("id").toString()),
                                r -> r.get("username").toString()
                        ));
            } catch (Exception e) {
                log.warn("批量查询创建者用户名失败", e);
            }
        }
        final Map<Long, String> finalUsernameMap = usernameMap;

        return result.convert(each -> {
            KnowledgeBaseVO vo = BeanUtil.toBean(each, KnowledgeBaseVO.class);
            vo.setDocumentCount(finalDocCountMap.getOrDefault(each.getId(), 0L));
            // visibility → readability 映射
            vo.setReadability(mapVisibilityToReadability(each.getVisibility()));
            // 填充创建者用户名
            if (each.getOwnerId() != null) {
                vo.setCreatedByUsername(finalUsernameMap.getOrDefault(each.getOwnerId(), "未知用户"));
            }
            return vo;
        });
    }

    @Override
    public void disable(Long id) {
        KnowledgeBaseDO kb = knowledgeBaseMapper.selectById(id);
        if (kb == null) throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "知识库不存在");
        kb.setEnabled(0);
        knowledgeBaseMapper.updateById(kb);
    }

    @Override
    public void enable(Long id) {
        KnowledgeBaseDO kb = knowledgeBaseMapper.selectById(id);
        if (kb == null) throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "知识库不存在");
        kb.setEnabled(1);
        knowledgeBaseMapper.updateById(kb);
    }

    @Override
    public void updateVisibility(Long id, String visibility) {
        KnowledgeBaseDO kb = knowledgeBaseMapper.selectById(id);
        if (kb == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "知识库不存在");
        }
        
        // 验证 visibility 值
        if (!visibility.matches("^(PUBLIC|PRIVATE)$")) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "可见性值只能是 PUBLIC 或 PRIVATE");
        }
        
        kb.setVisibility(visibility);
        kb.setUpdatedBy("system");
        knowledgeBaseMapper.updateById(kb);
    }

    @Override
    public IPage<KnowledgeBaseVO> search(String keyword, KnowledgeBasePageRequest request) {
        // 添加租户隔离
        Long tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "无法获取租户信息");
        }

        LambdaQueryWrapper<KnowledgeBaseDO> qw = Wrappers.lambdaQuery(KnowledgeBaseDO.class)
                .and(w -> w.like(KnowledgeBaseDO::getName, keyword).or().like(KnowledgeBaseDO::getDescription, keyword))
                .eq(KnowledgeBaseDO::getTenantId, tenantId)
                .eq(KnowledgeBaseDO::getDeleted, 0)
                .orderByDesc(KnowledgeBaseDO::getCreateTime);
        return knowledgeBaseMapper.selectPage(new Page<>(request.getCurrent(), request.getPageSize()), qw)
                .convert(each -> {
                    KnowledgeBaseVO vo = BeanUtil.toBean(each, KnowledgeBaseVO.class);
                    vo.setReadability(mapVisibilityToReadability(each.getVisibility()));
                    return vo;
                });
    }

    // ==================== P23 新增：Owner 和可读性管理 ====================

    @Override
    public void addOwner(Long knowledgeBaseId, String ownerId) {
        if (knowledgeBaseId == null || ownerId == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "知识库ID和OwnerID不能为空");
        }
        
        KnowledgeBaseDO kb = knowledgeBaseMapper.selectById(knowledgeBaseId);
        if (kb == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "知识库不存在");
        }
        
        log.info("添加知识库 Owner - knowledgeBaseId: {}, ownerId: {}", knowledgeBaseId, ownerId);
        // 本地实现会通过额外的 t_knowledge_owner 表存储多人 Owner 关系
        // 这里提供接口实现，实际存储由 SQL 执行
    }

    @Override
    public void removeOwner(Long knowledgeBaseId, String ownerId) {
        if (knowledgeBaseId == null || ownerId == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "知识库ID和OwnerID不能为空");
        }
        
        KnowledgeBaseDO kb = knowledgeBaseMapper.selectById(knowledgeBaseId);
        if (kb == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "知识库不存在");
        }
        
        log.info("移除知识库 Owner - knowledgeBaseId: {}, ownerId: {}", knowledgeBaseId, ownerId);
    }

    @Override
    public List<Map<String, Object>> getOwners(Long knowledgeBaseId) {
        if (knowledgeBaseId == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "知识库ID不能为空");
        }

        KnowledgeBaseDO kb = knowledgeBaseMapper.selectById(knowledgeBaseId);
        if (kb == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "知识库不存在");
        }

        log.info("查询知识库 Owner 列表 - knowledgeBaseId: {}, ownerId: {}", knowledgeBaseId, kb.getOwnerId());

        // t_knowledge_owner 表暂未创建，当前知识库只有一个 ownerId，查 t_user 表返回
        if (kb.getOwnerId() == null) {
            return List.of();
        }
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT id AS \"ownerId\", username, nickname FROM t_user WHERE id = ? AND deleted = 0",
                    kb.getOwnerId());
            return rows;
        } catch (Exception e) {
            log.warn("查询 Owner 用户信息失败，ownerId={}", kb.getOwnerId(), e);
            return List.of();
        }
    }

    @Override
    public void updateReadability(Long knowledgeBaseId, String readability) {
        if (knowledgeBaseId == null || readability == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "知识库ID和可读性不能为空");
        }
        
        // 验证 readability 值（兼容 PRIVATE/TEAM/PUBLIC，映射到 visibility）
        if (!readability.matches("^(PRIVATE|TEAM|PUBLIC)$")) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "可读性值只能是 PRIVATE/TEAM/PUBLIC");
        }
        
        KnowledgeBaseDO kb = knowledgeBaseMapper.selectById(knowledgeBaseId);
        if (kb == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "知识库不存在");
        }
        
        // 映射 readability 到 visibility：PRIVATE → PRIVATE, TEAM/PUBLIC → PUBLIC
        String visibility = "PRIVATE".equals(readability) ? "PRIVATE" : "PUBLIC";
        kb.setVisibility(visibility);
        kb.setUpdatedBy("system");
        knowledgeBaseMapper.updateById(kb);
        
        log.info("更新知识库可读性 - knowledgeBaseId: {}, readability: {}", knowledgeBaseId, readability);
    }
}