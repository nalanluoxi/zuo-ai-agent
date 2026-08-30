package com.example.zuoaiagent.knowledge.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.example.zuoaiagent.common.BaseResponse;
import com.example.zuoaiagent.common.ResultUtils;
import com.example.zuoaiagent.knowledge.model.request.KnowledgeBaseCreateRequest;
import com.example.zuoaiagent.knowledge.model.request.KnowledgeBasePageRequest;
import com.example.zuoaiagent.knowledge.model.request.KnowledgeBaseUpdateRequest;
import com.example.zuoaiagent.knowledge.model.vo.KnowledgeBaseVO;
import com.example.zuoaiagent.knowledge.service.KnowledgeBaseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 知识库管理接口
 */
@RestController
@RequestMapping("/knowledge-base")
@RequiredArgsConstructor
public class KnowledgeBaseController {

    private final KnowledgeBaseService knowledgeBaseService;

    /**
     * 创建知识库
     */
    @PostMapping
    public BaseResponse<Long> create(@Valid @RequestBody KnowledgeBaseCreateRequest request) {
        return ResultUtils.success(knowledgeBaseService.create(request));
    }

    /**
     * 修改知识库
     * 优化点：移除 URL 中的 /{id}，改为全 JSON 传参
     */
    @PutMapping
    public BaseResponse<Boolean> update(@Valid @RequestBody KnowledgeBaseUpdateRequest request) {
        // 直接从 request 对象中获取 ID 进行逻辑处理
        knowledgeBaseService.update(request.getId(), request);
        return ResultUtils.success(true);
    }

    /**
     * 删除知识库
     */
    @DeleteMapping("/{id}")
    public BaseResponse<Boolean> delete(@PathVariable Long id) {
        knowledgeBaseService.delete(id);
        return ResultUtils.success(true);
    }

    /**
     * 查询知识库详情
     */
    @GetMapping("/{id}")
    public BaseResponse<KnowledgeBaseVO> getById(@PathVariable Long id) {
        return ResultUtils.success(knowledgeBaseService.getById(id));
    }

    /**
     * 分页查询知识库列表
     */
    @GetMapping("/page")
    public BaseResponse<IPage<KnowledgeBaseVO>> page(KnowledgeBasePageRequest request) {
        return ResultUtils.success(knowledgeBaseService.page(request));
    }

    @PutMapping("/{id}/disable")
    public BaseResponse<Boolean> disable(@PathVariable Long id) {
        knowledgeBaseService.disable(id);
        return ResultUtils.success(true);
    }

    @PutMapping("/{id}/enable")
    public BaseResponse<Boolean> enable(@PathVariable Long id) {
        knowledgeBaseService.enable(id);
        return ResultUtils.success(true);
    }

    @GetMapping("/search")
    public BaseResponse<IPage<KnowledgeBaseVO>> search(@RequestParam String keyword, KnowledgeBasePageRequest request) {
        return ResultUtils.success(knowledgeBaseService.search(keyword, request));
    }

    /**
     * 修改知识库可见状态
     * P2/P3 修复：新增接口支持修改 visibility 字段
     */
    @PutMapping("/{id}/visibility")
    public BaseResponse<Boolean> updateVisibility(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        String visibility = body.get("visibility");
        knowledgeBaseService.updateVisibility(id, visibility);
        return ResultUtils.success(true);
    }

    // ==================== P23 增强：知识库 Owner 和可读性管理 ====================

    /**
     * P23：添加知识库 Owner（支持多人管理）
     */
    @PostMapping("/{id}/owners")
    public BaseResponse<Boolean> addOwner(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        String ownerId = body.get("ownerId");
        knowledgeBaseService.addOwner(id, ownerId);
        return ResultUtils.success(true);
    }

    /**
     * P23：移除知识库 Owner
     */
    @DeleteMapping("/{id}/owners/{ownerId}")
    public BaseResponse<Boolean> removeOwner(
            @PathVariable Long id,
            @PathVariable String ownerId) {
        knowledgeBaseService.removeOwner(id, ownerId);
        return ResultUtils.success(true);
    }

    /**
     * P23：获取知识库所有 Owner 列表
     */
    @GetMapping("/{id}/owners")
    public BaseResponse<java.util.List<Map<String, Object>>> getOwners(@PathVariable Long id) {
        return ResultUtils.success(knowledgeBaseService.getOwners(id));
    }

    /**
     * P23：更新知识库可读性（PRIVATE/TEAM/PUBLIC）
     */
    @PutMapping("/{id}/readability")
    public BaseResponse<Boolean> updateReadability(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        String readability = body.get("readability");
        knowledgeBaseService.updateReadability(id, readability);
        return ResultUtils.success(true);
    }
}