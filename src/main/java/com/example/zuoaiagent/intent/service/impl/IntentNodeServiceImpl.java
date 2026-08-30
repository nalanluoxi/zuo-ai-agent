package com.example.zuoaiagent.intent.service.impl;

import com.example.zuoaiagent.exception.BusinessException;
import com.example.zuoaiagent.exception.ErrorCode;
import com.example.zuoaiagent.intent.service.IntentNodeService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * P27：意图树服务实现
 */
@Service
@RequiredArgsConstructor
public class IntentNodeServiceImpl implements IntentNodeService {

    private static final Logger log = LoggerFactory.getLogger(IntentNodeServiceImpl.class);
    private final JdbcTemplate jdbcTemplate;

    @Override
    public List<Map<String, Object>> getIntentTree() {
        try {
            // 查询所有根节点（parent_id = NULL）
            String rootSql = "SELECT id, name, parent_id, description, sort_order, created_at, updated_at " +
                            "FROM t_intent_node " +
                            "WHERE parent_id IS NULL AND deleted = 0 " +
                            "ORDER BY sort_order ASC";
            
            List<Map<String, Object>> rootNodes = jdbcTemplate.queryForList(rootSql);
            
            // 为每个根节点递归构建树
            List<Map<String, Object>> tree = new ArrayList<>();
            for (Map<String, Object> rootNode : rootNodes) {
                Map<String, Object> treeNode = new LinkedHashMap<>(rootNode);
                treeNode.put("children", buildChildrenTree((Long) rootNode.get("id")));
                tree.add(treeNode);
            }
            
            return tree;
        } catch (Exception e) {
            log.error("查询意图树失败：", e);
            return List.of();
        }
    }

    /**
     * 递归构建子节点树
     */
    private List<Map<String, Object>> buildChildrenTree(Long parentId) {
        String sql = "SELECT id, name, parent_id, description, sort_order, created_at, updated_at " +
                    "FROM t_intent_node " +
                    "WHERE parent_id = ? AND deleted = 0 " +
                    "ORDER BY sort_order ASC";
        
        List<Map<String, Object>> children = jdbcTemplate.queryForList(sql, parentId);
        
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> child : children) {
            Map<String, Object> childNode = new LinkedHashMap<>(child);
            childNode.put("children", buildChildrenTree((Long) child.get("id")));
            result.add(childNode);
        }
        
        return result;
    }

    @Override
    public Map<String, Object> getNodeDetail(Long nodeId) {
        try {
            String sql = "SELECT id, name, parent_id, description, sort_order, created_at, updated_at " +
                        "FROM t_intent_node " +
                        "WHERE id = ? AND deleted = 0";
            
            List<Map<String, Object>> results = jdbcTemplate.queryForList(sql, nodeId);
            
            if (results.isEmpty()) {
                throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "节点不存在");
            }
            
            return results.get(0);
        } catch (Exception e) {
            if (e instanceof BusinessException) throw e;
            log.error("查询节点详情失败：", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "查询节点详情失败");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> createNode(String name, Long parentId, String description) {
        try {
            Long nodeId = System.currentTimeMillis();
            
            // 验证父节点存在
            if (parentId != null) {
                String checkSql = "SELECT COUNT(*) as cnt FROM t_intent_node WHERE id = ? AND deleted = 0";
                Integer count = jdbcTemplate.queryForObject(checkSql, Integer.class, parentId);
                if (count == null || count == 0) {
                    throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "父节点不存在");
                }
            }
            
            // 获取下一个 sort_order
            Integer nextOrder = 0;
            if (parentId != null) {
                String orderSql = "SELECT COALESCE(MAX(sort_order), -1) + 1 as next_order " +
                                 "FROM t_intent_node WHERE parent_id = ? AND deleted = 0";
                Integer order = jdbcTemplate.queryForObject(orderSql, Integer.class, parentId);
                nextOrder = order != null ? order : 0;
            }
            
            String insertSql = "INSERT INTO t_intent_node (id, name, parent_id, description, sort_order, deleted, created_at, updated_at) " +
                              "VALUES (?, ?, ?, ?, ?, 0, NOW(), NOW())";
            
            jdbcTemplate.update(insertSql, nodeId, name, parentId, description, nextOrder);
            
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("id", nodeId);
            result.put("name", name);
            result.put("parentId", parentId);
            result.put("description", description);
            result.put("sortOrder", nextOrder);
            result.put("createdAt", LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME));
            
            return result;
        } catch (Exception e) {
            if (e instanceof BusinessException) throw e;
            log.error("创建节点失败：", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "创建节点失败");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> updateNode(Long nodeId, String name, String description) {
        try {
            String updateSql = "UPDATE t_intent_node SET name = ?, description = ?, updated_at = NOW() " +
                              "WHERE id = ? AND deleted = 0";
            
            int rows = jdbcTemplate.update(updateSql, name, description, nodeId);
            
            if (rows == 0) {
                throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "节点不存在");
            }
            
            // 返回更新后的节点
            return getNodeDetail(nodeId);
        } catch (Exception e) {
            if (e instanceof BusinessException) throw e;
            log.error("更新节点失败：", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "更新节点失败");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteNode(Long nodeId) {
        try {
            // 验证节点存在
            String checkSql = "SELECT COUNT(*) as cnt FROM t_intent_node WHERE id = ? AND deleted = 0";
            Integer count = jdbcTemplate.queryForObject(checkSql, Integer.class, nodeId);
            if (count == null || count == 0) {
                throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "节点不存在");
            }
            
            // 递归删除所有子节点
            deleteNodeRecursive(nodeId);
            
            log.info("删除意图节点 - nodeId: {}", nodeId);
        } catch (Exception e) {
            if (e instanceof BusinessException) throw e;
            log.error("删除节点失败：", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "删除节点失败");
        }
    }

    /**
     * 递归删除节点及其所有子节点
     */
    private void deleteNodeRecursive(Long nodeId) {
        // 先查询所有子节点
        String childSql = "SELECT id FROM t_intent_node WHERE parent_id = ? AND deleted = 0";
        List<Map<String, Object>> children = jdbcTemplate.queryForList(childSql, nodeId);
        
        // 递归删除子节点
        for (Map<String, Object> child : children) {
            deleteNodeRecursive((Long) child.get("id"));
        }
        
        // 逻辑删除当前节点
        String deleteSql = "UPDATE t_intent_node SET deleted = 1 WHERE id = ?";
        jdbcTemplate.update(deleteSql, nodeId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> moveNode(Long nodeId, Long newParentId) {
        try {
            // 验证节点存在
            Map<String, Object> node = getNodeDetail(nodeId);
            
            // 验证新父节点存在（如果指定了）
            if (newParentId != null) {
                String checkSql = "SELECT COUNT(*) as cnt FROM t_intent_node WHERE id = ? AND deleted = 0";
                Integer count = jdbcTemplate.queryForObject(checkSql, Integer.class, newParentId);
                if (count == null || count == 0) {
                    throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "新父节点不存在");
                }
            }
            
            // 更新父节点
            String updateSql = "UPDATE t_intent_node SET parent_id = ?, updated_at = NOW() WHERE id = ?";
            jdbcTemplate.update(updateSql, newParentId, nodeId);
            
            return getNodeDetail(nodeId);
        } catch (Exception e) {
            if (e instanceof BusinessException) throw e;
            log.error("移动节点失败：", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "移动节点失败");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> reorderNode(Long nodeId, Integer sortOrder) {
        try {
            String updateSql = "UPDATE t_intent_node SET sort_order = ?, updated_at = NOW() WHERE id = ? AND deleted = 0";
            
            int rows = jdbcTemplate.update(updateSql, sortOrder, nodeId);
            
            if (rows == 0) {
                throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "节点不存在");
            }
            
            return getNodeDetail(nodeId);
        } catch (Exception e) {
            if (e instanceof BusinessException) throw e;
            log.error("重新排序节点失败：", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "重新排序节点失败");
        }
    }

    @Override
    public List<Map<String, Object>> getChildren(Long nodeId) {
        try {
            // 验证父节点存在
            getNodeDetail(nodeId);
            
            String sql = "SELECT id, name, parent_id, description, sort_order, created_at, updated_at " +
                        "FROM t_intent_node " +
                        "WHERE parent_id = ? AND deleted = 0 " +
                        "ORDER BY sort_order ASC";
            
            return jdbcTemplate.queryForList(sql, nodeId);
        } catch (Exception e) {
            if (e instanceof BusinessException) throw e;
            log.error("查询子节点失败：", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "查询子节点失败");
        }
    }

    @Override
    public List<Map<String, Object>> getNodePath(Long nodeId) {
        try {
            List<Map<String, Object>> path = new ArrayList<>();
            
            // 从当前节点开始，向上查询到根节点
            Long currentId = nodeId;
            while (currentId != null) {
                String sql = "SELECT id, name, parent_id, description FROM t_intent_node WHERE id = ? AND deleted = 0";
                List<Map<String, Object>> results = jdbcTemplate.queryForList(sql, currentId);
                
                if (results.isEmpty()) {
                    break;
                }
                
                Map<String, Object> node = results.get(0);
                path.add(0, node); // 插入到前面，保持从根到叶的顺序
                
                currentId = (Long) node.get("parent_id");
            }
            
            return path;
        } catch (Exception e) {
            log.error("查询节点路径失败：", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "查询节点路径失败");
        }
    }
}
