package com.example.zuoaiagent.intent.service;

import java.util.List;
import java.util.Map;

/**
 * P27：意图树服务接口
 */
public interface IntentNodeService {

    /**
     * 获取完整的意图树（嵌套结构）
     */
    List<Map<String, Object>> getIntentTree();

    /**
     * 获取节点详情
     */
    Map<String, Object> getNodeDetail(Long nodeId);

    /**
     * 创建节点
     */
    Map<String, Object> createNode(String name, Long parentId, String description);

    /**
     * 更新节点
     */
    Map<String, Object> updateNode(Long nodeId, String name, String description);

    /**
     * 删除节点（级联删除）
     */
    void deleteNode(Long nodeId);

    /**
     * 移动节点
     */
    Map<String, Object> moveNode(Long nodeId, Long newParentId);

    /**
     * 重新排序节点
     */
    Map<String, Object> reorderNode(Long nodeId, Integer sortOrder);

    /**
     * 获取子节点列表
     */
    List<Map<String, Object>> getChildren(Long nodeId);

    /**
     * 获取节点完整路径
     */
    List<Map<String, Object>> getNodePath(Long nodeId);
}
