package com.example.zuoaiagent.intent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.zuoaiagent.intent.entity.IntentNodeDO;
import com.example.zuoaiagent.intent.mapper.IntentNodeMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
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
        Map<Long, IntentNodeDO> cache = nodeCache;
        if (cache.isEmpty()) {
            return "（意图树为空）";
        }

        // 统计哪些节点有子节点，用于判断叶子节点
        java.util.Set<Long> hasChildren = new java.util.HashSet<>();
        for (IntentNodeDO node : cache.values()) {
            if (node.getParentId() != null) {
                hasChildren.add(node.getParentId());
            }
        }

        StringBuilder sb = new StringBuilder();
        List<IntentNodeDO> sorted = new ArrayList<>(cache.values());
        sorted.sort(Comparator.comparingInt((IntentNodeDO n) -> n.getLevel())
                .thenComparingInt(n -> n.getSortOrder() == null ? 0 : n.getSortOrder()));

        for (IntentNodeDO node : sorted) {
            int indentLevel = node.getLevel() == null ? 0 : Math.max(0, node.getLevel() - 1);
            String indent = "  ".repeat(indentLevel);
            sb.append(indent)
              .append("[ID=").append(node.getId()).append("] ");

            // 构造层次路径
            String path = buildPath(node, cache);
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
