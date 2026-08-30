package com.example.zuoaiagent.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.zuoaiagent.common.BaseResponse;
import com.example.zuoaiagent.common.ResultUtils;
import com.example.zuoaiagent.intent.entity.IntentNodeDO;
import com.example.zuoaiagent.intent.mapper.IntentNodeMapper;
import com.example.zuoaiagent.intent.service.IntentTreeService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 意图节点管理接口
 *
 * <p>提供意图树 CRUD 及缓存刷新接口，支持动态调整意图分类配置。
 */
@RestController
@RequestMapping("/intent")
public class IntentNodeController {

    private final IntentNodeMapper intentNodeMapper;
    private final IntentTreeService intentTreeService;

    public IntentNodeController(IntentNodeMapper intentNodeMapper, IntentTreeService intentTreeService) {
        this.intentNodeMapper = intentNodeMapper;
        this.intentTreeService = intentTreeService;
    }

    /**
     * 创建意图节点
     */
    @PostMapping("/node")
    public BaseResponse<Long> createNode(@RequestBody IntentNodeDO node) {
        intentNodeMapper.insert(node);
        return ResultUtils.success(node.getId());
    }

    /**
     * 更新意图节点（id 在 RequestBody 中传入，null 或空字符串字段不更新）
     */
    @PutMapping("/node")
    public BaseResponse<Boolean> updateNode(@RequestBody IntentNodeDO node) {
        // 空字符串置 null，MyBatis-Plus updateById 会跳过 null 字段
        if (node.getLabel() != null && node.getLabel().isBlank()) node.setLabel(null);
        if (node.getDescription() != null && node.getDescription().isBlank()) node.setDescription(null);
        int rows = intentNodeMapper.updateById(node);
        return ResultUtils.success(rows > 0);
    }

    /**
     * 删除意图节点（逻辑删除，id 在 RequestBody 中传入）
     */
    @DeleteMapping("/node")
    public BaseResponse<Boolean> deleteNode(@RequestBody IntentNodeDO node) {
        IntentNodeDO update = new IntentNodeDO();
        update.setId(node.getId());
        update.setDeleted((short) 1);
        int rows = intentNodeMapper.updateById(update);
        return ResultUtils.success(rows > 0);
    }

    /**
     * 查询全量意图节点（按层级排序）
     */
    @GetMapping("/nodes")
    public BaseResponse<List<IntentNodeDO>> listNodes() {
        List<IntentNodeDO> nodes = intentNodeMapper.selectList(
                new LambdaQueryWrapper<IntentNodeDO>()
                        .eq(IntentNodeDO::getDeleted, (short) 0)
                        .orderByAsc(IntentNodeDO::getLevel)
                        .orderByAsc(IntentNodeDO::getSortOrder)
        );
        return ResultUtils.success(nodes);
    }

    /**
     * 手动刷新意图树缓存
     */
    @PostMapping("/cache/refresh")
    public BaseResponse<String> refreshCache() {
        intentTreeService.refreshCache();
        return ResultUtils.success("缓存刷新成功，共加载 " + intentTreeService.getAllNodes().size() + " 个节点");
    }

    @PutMapping("/node/{id}")
    public BaseResponse<Boolean> updateNode(@PathVariable Long id, @RequestBody IntentNodeDO node) {
        // P4 修复：如果修改了 parentId，进行树结构校验
        if (node.getParentId() != null && !node.getParentId().equals(intentNodeMapper.selectById(id).getParentId())) {
            intentTreeService.validateMove(id, node.getParentId());
        }
        
        node.setId(id);
        intentNodeMapper.updateById(node);
        intentTreeService.refreshCache();
        return ResultUtils.success(true);
    }

    @DeleteMapping("/node/{id}")
    public BaseResponse<Boolean> deleteNode(@PathVariable Long id) {
        // P5 修复：使用新的 deleteNode 方法，包含子节点检查和逻辑删除
        intentTreeService.deleteNode(id);
        return ResultUtils.success(true);
    }

    @PutMapping("/node/{id}/disable")
    public BaseResponse<Boolean> disable(@PathVariable Long id) {
        IntentNodeDO node = intentNodeMapper.selectById(id);
        if (node != null) { node.setEnabled(0); intentNodeMapper.updateById(node); intentTreeService.refreshCache(); }
        return ResultUtils.success(true);
    }

    @PutMapping("/node/{id}/enable")
    public BaseResponse<Boolean> enable(@PathVariable Long id) {
        IntentNodeDO node = intentNodeMapper.selectById(id);
        if (node != null) { node.setEnabled(1); intentNodeMapper.updateById(node); intentTreeService.refreshCache(); }
        return ResultUtils.success(true);
    }
}