package com.example.zuoaiagent.knowledge.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.example.zuoaiagent.common.BaseResponse;
import com.example.zuoaiagent.common.ResultUtils;
import com.example.zuoaiagent.knowledge.model.request.KnowledgeDocumentPageRequest;
import com.example.zuoaiagent.knowledge.model.vo.KnowledgeDocumentVO;
import com.example.zuoaiagent.knowledge.service.KnowledgeDocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 知识库文档管理接口
 */
@RestController
@RequestMapping("/knowledge-base")
@RequiredArgsConstructor
public class KnowledgeDocumentController {

    private final KnowledgeDocumentService knowledgeDocumentService;

    /**
     * 上传文档到指定知识库
     */
    @PostMapping(value = "/{kbId}/docs/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public BaseResponse<KnowledgeDocumentVO> upload(@PathVariable Long kbId,
                                                    @RequestPart("file") MultipartFile file) {
        return ResultUtils.success(knowledgeDocumentService.upload(kbId, file));
    }

    /**
     * 删除文档
     */
    @DeleteMapping("/docs/{docId}")
    public BaseResponse<Boolean> delete(@PathVariable Long docId) {
        knowledgeDocumentService.delete(docId);
        return ResultUtils.success(true);
    }

    /**
     * 查询文档详情
     */
    @GetMapping("/docs/{docId}")
    public BaseResponse<KnowledgeDocumentVO> getById(@PathVariable Long docId) {
        return ResultUtils.success(knowledgeDocumentService.getById(docId));
    }

    /**
     * 分页查询知识库下的文档列表
     */
    @GetMapping("/{kbId}/docs")
    public BaseResponse<IPage<KnowledgeDocumentVO>> page(@PathVariable Long kbId,
                                                         KnowledgeDocumentPageRequest request) {
        return ResultUtils.success(knowledgeDocumentService.page(kbId, request));
    }
}