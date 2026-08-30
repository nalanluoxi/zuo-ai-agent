package com.example.zuoaiagent.approval.controller;

import com.example.zuoaiagent.common.BaseResponse;
import com.example.zuoaiagent.common.ResultUtils;
import com.example.zuoaiagent.exception.BusinessException;
import com.example.zuoaiagent.exception.ErrorCode;
import com.example.zuoaiagent.approval.service.ApprovalService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * P28：审批 API
 * 功能：创建、查询、条件审批、拒绝、状态更新
 */
@RestController
@RequestMapping("/approvals")
@RequiredArgsConstructor
public class ApprovalController {

    private final ApprovalService approvalService;

    /**
     * P28：获取审批列表（分页）
     * @param page 分页页码
     * @param size 每页大小
     * @param status 审批状态（可选：PENDING/APPROVED/REJECTED）
     */
    @GetMapping
    public BaseResponse<Map<String, Object>> listApprovals(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status) {
        
        if (page < 1) page = 1;
        if (size < 1 || size > 100) size = 20;
        
        return ResultUtils.success(approvalService.listApprovals(page, size, status));
    }

    /**
     * P28：获取审批详情
     * @param approvalId 审批 ID
     */
    @GetMapping("/{approvalId}")
    public BaseResponse<Map<String, Object>> getApprovalDetail(@PathVariable Long approvalId) {
        if (approvalId == null || approvalId <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "审批 ID 无效");
        }
        
        return ResultUtils.success(approvalService.getApprovalDetail(approvalId));
    }

    /**
     * P28：创建审批工单
     * @param request 包含 title, description, applicantId, appliedData 等
     */
    @PostMapping
    public BaseResponse<Map<String, Object>> createApproval(@RequestBody Map<String, Object> request) {
        String title = (String) request.get("title");
        String description = (String) request.get("description");
        String applicantId = (String) request.get("applicantId");
        
        if (title == null || title.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "标题不能为空");
        }
        
        if (applicantId == null || applicantId.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "申请人 ID 不能为空");
        }
        
        return ResultUtils.success(approvalService.createApproval(title, description, applicantId, request));
    }

    /**
     * P28：条件审批（支持权限子集选择）
     * @param approvalId 审批 ID
     * @param request 包含 approverId, approvalRemark, selectedPermissions（可选）
     */
    @PostMapping("/{approvalId}/approve")
    public BaseResponse<Map<String, Object>> approveWithConditions(
            @PathVariable Long approvalId,
            @RequestBody Map<String, Object> request) {
        
        String approverId = (String) request.get("approverId");
        String remark = (String) request.get("approvalRemark");
        
        if (approverId == null || approverId.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "审批人 ID 不能为空");
        }
        
        return ResultUtils.success(approvalService.approveWithConditions(approvalId, approverId, remark, request));
    }

    /**
     * P28：拒绝审批（带拒绝原因）
     * @param approvalId 审批 ID
     * @param request 包含 approverId, rejectionReason
     */
    @PostMapping("/{approvalId}/reject")
    public BaseResponse<Map<String, Object>> rejectApproval(
            @PathVariable Long approvalId,
            @RequestBody Map<String, Object> request) {
        
        String approverId = (String) request.get("approverId");
        String rejectionReason = (String) request.get("rejectionReason");
        
        if (approverId == null || approverId.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "审批人 ID 不能为空");
        }
        
        if (rejectionReason == null || rejectionReason.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "拒绝原因不能为空");
        }
        
        return ResultUtils.success(approvalService.rejectApproval(approvalId, approverId, rejectionReason));
    }

    /**
     * P28：更新审批状态
     * @param approvalId 审批 ID
     * @param request 包含新的 status
     */
    @PutMapping("/{approvalId}/status")
    public BaseResponse<Map<String, Object>> updateApprovalStatus(
            @PathVariable Long approvalId,
            @RequestBody Map<String, String> request) {
        
        String status = request.get("status");
        
        if (status == null || status.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "状态不能为空");
        }
        
        return ResultUtils.success(approvalService.updateApprovalStatus(approvalId, status));
    }

    /**
     * P28：获取当前用户待审批列表
     * @param userId 用户 ID
     * @param page 分页页码
     * @param size 每页大小
     */
    @GetMapping("/pending/{userId}")
    public BaseResponse<Map<String, Object>> getPendingApprovals(
            @PathVariable String userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        if (userId == null || userId.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户 ID 不能为空");
        }
        
        if (page < 1) page = 1;
        if (size < 1 || size > 100) size = 20;
        
        return ResultUtils.success(approvalService.getPendingApprovals(userId, page, size));
    }
}
