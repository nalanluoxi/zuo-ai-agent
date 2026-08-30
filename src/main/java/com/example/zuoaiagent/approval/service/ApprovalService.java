package com.example.zuoaiagent.approval.service;

import java.util.Map;

/**
 * P28：审批服务接口
 */
public interface ApprovalService {

    /**
     * 分页查询审批列表
     */
    Map<String, Object> listApprovals(int page, int size, String status);

    /**
     * 获取审批详情
     */
    Map<String, Object> getApprovalDetail(Long approvalId);

    /**
     * 创建审批工单
     */
    Map<String, Object> createApproval(String title, String description, String applicantId, Map<String, Object> data);

    /**
     * 条件审批
     */
    Map<String, Object> approveWithConditions(Long approvalId, String approverId, String remark, Map<String, Object> conditions);

    /**
     * 拒绝审批
     */
    Map<String, Object> rejectApproval(Long approvalId, String approverId, String rejectionReason);

    /**
     * 更新审批状态
     */
    Map<String, Object> updateApprovalStatus(Long approvalId, String status);

    /**
     * 获取待审批列表
     */
    Map<String, Object> getPendingApprovals(String userId, int page, int size);
}
