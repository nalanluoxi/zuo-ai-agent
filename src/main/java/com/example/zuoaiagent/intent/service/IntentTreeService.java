package com.example.zuoaiagent.intent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.zuoaiagent.config.TenantContextHolder;
import com.example.zuoaiagent.intent.entity.IntentNodeDO;
import com.example.zuoaiagent.intent.mapper.IntentNodeMapper;
import com.example.zuoaiagent.exception.BusinessException;
import com.example.zuoaiagent.exception.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 意图树加载与缓存服务
 *
 * <p>启动时从 t_intent_node 加载全量节点到内存 Map，并定时刷新。
 * 提供 {@link #buildTreeText()} 方法将节点树格式化为 LLM 分类用的纯文本。
 */
@Service
public class IntentTreeService {

    private static final Logger log = LoggerFactory.getLogger(IntentTreeService.class);

    private final IntentNodeMapper intentNodeMapper;

    /** 全量节点缓存：nodeId → IntentNodeDO */
    private volatile Map<Long, IntentNodeDO> nodeCache = new ConcurrentHashMap<>();

    public IntentTreeService(IntentNodeMapper intentNodeMapper) {
        this.intentNodeMapper = intentNodeMapper;
        refreshCache();
    }

    /**
     * 刷新意图节点缓存（启动时 + 定时）。
     * 定时间隔由 application.yaml 的 intent.cache.refresh-interval-ms 控制，默认 5 分钟。
     */
    @Scheduled(fixedDelayString = "${intent.cache.refresh-interval-ms:300000}")
    public void refreshCache() {
        try {
            List<IntentNodeDO> nodes = intentNodeMapper.selectList(
                    new LambdaQueryWrapper<IntentNodeDO>()
                            .eq(IntentNodeDO::getDeleted, (short) 0)
                            .orderByAsc(IntentNodeDO::getLevel)
                            .orderByAsc(IntentNodeDO::getSortOrder)
            );
            Map<Long, IntentNodeDO> newCache = new ConcurrentHashMap<>();
            for (IntentNodeDO node : nodes) {
                newCache.put(node.getId(), node);
            }
            nodeCache = newCache;
            log.info("[IntentTreeService] 意图缓存刷新完成，共 {} 个节点", newCache.size());
        } catch (Exception e) {
            log.error("[IntentTreeService] 意图缓存刷新失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 根据节点 ID 获取节点。
     */
    public IntentNodeDO getNode(Long nodeId) {
        return nodeCache.get(nodeId);
    }

    /**
     * 获取全量节点（副本列表）。
     */
    public List<IntentNodeDO> getAllNodes() {
        return new ArrayList<>(nodeCache.values());
    }

    /**
     * 将意图树格式化为 LLM 分类 Prompt 中使用的纯文本。
     *
     * <p>格式示例：
     * <pre>
     * [ID=1] 闲聊/通用 (系统节点): 用户问候、闲聊...
     * [ID=2] 金融: 金融工程...
     *   [ID=3] 金融 > 金融工程: 金融工程技术...
     *   [ID=4] 金融 > 投资估价: 股票估值...
     * </pre>
     */
    public String buildTreeText() {
        // P19-P21 修复：过滤当前租户的节点
        Long tenantId = TenantContextHolder.getTenantId();
        
        Map<Long, IntentNodeDO> cache = nodeCache;
        if (cache.isEmpty()) {
            return "（意图树为空）";
        }
        
        // 只使用当前租户的节点
        Map<Long, IntentNodeDO> tenantCache = new java.util.HashMap<>();
        for (IntentNodeDO node : cache.values()) {
            if (Objects.equals(node.getTenantId(), tenantId)) {
                tenantCache.put(node.getId(), node);
            }
        }
        
        if (tenantCache.isEmpty()) {
            return "（意图树为空）";
        }

        // 统计哪些节点有子节点，用于判断叶子节点
        java.util.Set<Long> hasChildren = new java.util.HashSet<>();
        for (IntentNodeDO node : tenantCache.values()) {
            if (node.getParentId() != null) {
                hasChildren.add(node.getParentId());
            }
        }

        StringBuilder sb = new StringBuilder();
        List<IntentNodeDO> sorted = new ArrayList<>(tenantCache.values());
        sorted.sort(Comparator.comparingInt((IntentNodeDO n) -> n.getLevel())
                .thenComparingInt(n -> n.getSortOrder() == null ? 0 : n.getSortOrder()));

        for (IntentNodeDO node : sorted) {
            int indentLevel = node.getLevel() == null ? 0 : Math.max(0, node.getLevel() - 1);
            String indent = "  ".repeat(indentLevel);
            sb.append(indent)
              .append("[ID=").append(node.getId()).append("] ");

            // 构造层次路径
            String path = buildPath(node, tenantCache);
            sb.append(path);

            if (node.getIsSystem() != null && node.getIsSystem() == 1) {
                sb.append(" (系统节点)");
            } else if (!hasChildren.contains(node.getId())) {
                // 叶子节点明确标注，提示 LLM 优先选择
                sb.append(" ★叶子节点");
            }

            if (node.getDescription() != null && !node.getDescription().isBlank()) {
                sb.append(": ").append(node.getDescription());
            }
            sb.append("\n");
        }
        return sb.toString().trim();
    }

    // -------------------- P4: 树结构校验方法 --------------------

    /**
     * P4 修复：验证节点是否能移动到指定父节点
     * 检查：1. 不能自指  2. 不能环形  3. 层级检查 4. 租户隔离
     */
    public void validateMove(Long nodeId, Long newParentId) {
        if (nodeId == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "节点 ID 不能为空");
        }
        // newParentId == null 表示移动到根节点，合法操作，无需额外校验
        if (newParentId == null) {
            return;
        }
        
        Long tenantId = TenantContextHolder.getTenantId();
        
        // 校验 1: 不能自指
        if (nodeId.equals(newParentId)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "不能成为自己的子节点");
        }
        
        // 校验 2: 检查环形结构 - 检查 newParentId 的所有祖先是否包含 nodeId
        Set<Long> ancestors = getAncestors(newParentId);
        if (ancestors.contains(nodeId)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "不能创建环形结构");
        }
        
        // 校验 3: 层级检查 + 租户隔离
        IntentNodeDO node = nodeCache.get(nodeId);
        IntentNodeDO newParent = nodeCache.get(newParentId);
        
        if (node == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "原节点不存在");
        }
        if (newParent == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "父节点不存在");
        }
        
        // P19-P21 修复：租户校验 - 防止跨租户移动节点
        if (!Objects.equals(node.getTenantId(), tenantId)) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "无权移动此节点");
        }
        if (!Objects.equals(newParent.getTenantId(), tenantId)) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "无权移动到此父节点");
        }
        
        Short nodeLevel = node.getLevel() != null ? node.getLevel() : 1;
        Short parentLevel = newParent.getLevel() != null ? newParent.getLevel() : 0;
        
        if (nodeLevel <= parentLevel) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, 
                "子节点的层级必须大于父节点（node.level=" + nodeLevel + ", parent.level=" + parentLevel + "）");
        }
    }
    
    /**
     * 递归获取所有祖先节点 ID
     */
    private Set<Long> getAncestors(Long nodeId) {
        Set<Long> ancestors = new HashSet<>();
        IntentNodeDO node = nodeCache.get(nodeId);
        
        int maxDepth = 100; // 防止无限循环
        int depth = 0;
        
        while (node != null && node.getParentId() != null && depth < maxDepth) {
            ancestors.add(node.getParentId());
            node = nodeCache.get(node.getParentId());
            depth++;
        }
        
        return ancestors;
    }

    // -------------------- P5: 删除方法 --------------------

    /**
     * P5 修复：删除意图节点（逻辑删除 + 子节点检查 + 租户隔离）
     */
    public void deleteNode(Long nodeId) {
        if (nodeId == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "节点 ID 不能为空");
        }

        Long tenantId = TenantContextHolder.getTenantId();

        IntentNodeDO node = nodeCache.get(nodeId);
        if (node == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "节点不存在");
        }

        // P19-P21 修复：租户校验 - 严格模式
        if (!Objects.equals(node.getTenantId(), tenantId)) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "无权删除此节点");
        }
        
        // 检查是否有子节点
        long childCount = intentNodeMapper.selectCount(
            new LambdaQueryWrapper<IntentNodeDO>()
                .eq(IntentNodeDO::getParentId, nodeId)
                .eq(IntentNodeDO::getDeleted, (short) 0)
        );
        
        if (childCount > 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, 
                "节点存在 " + childCount + " 个子节点，无法删除，请先删除所有子节点");
        }
        
        // 执行逻辑删除
        // 注意：@TableLogic 字段不会被 updateById 写入 SET 子句，必须调用 deleteById 才能真正逻辑删除
        intentNodeMapper.deleteById(nodeId);
        
        // 刷新缓存
        refreshCache();
    }

    // -------------------- 私有方法 --------------------

    private String buildPath(IntentNodeDO node, Map<Long, IntentNodeDO> cache) {
        List<String> parts = new ArrayList<>();
        IntentNodeDO current = node;
        while (current != null) {
            parts.add(0, current.getLabel());
            if (current.getParentId() == null) break;
            current = cache.get(current.getParentId());
        }
        return String.join(" > ", parts);
    }
}
