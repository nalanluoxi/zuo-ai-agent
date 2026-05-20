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
import org.springframework.web.bind.annotation.RestController;

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
}