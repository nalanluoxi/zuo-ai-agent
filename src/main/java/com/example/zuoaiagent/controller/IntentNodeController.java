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
     * 更新意图节点
     */
    @PutMapping("/node/{id}")
    public BaseResponse<Boolean> updateNode(@PathVariable Long id, @RequestBody IntentNodeDO node) {
        node.setId(id);
        int rows = intentNodeMapper.updateById(node);
        return ResultUtils.success(rows > 0);
    }

    /**
     * 删除意图节点（逻辑删除）
     */
    @DeleteMapping("/node/{id}")
    public BaseResponse<Boolean> deleteNode(@PathVariable Long id) {
        IntentNodeDO node = new IntentNodeDO();
        node.setId(id);
        node.setDeleted((short) 1);
        int rows = intentNodeMapper.updateById(node);
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
}
