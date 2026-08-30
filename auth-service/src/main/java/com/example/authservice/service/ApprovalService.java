package com.example.authservice.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.authservice.entity.*;
import com.example.authservice.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ApprovalService {
    private final ApprovalMapper approvalMapper;
    private final AuditLogMapper auditLogMapper;

    @Transactional
    public ApprovalDO submit(String applyType, String targetType, Long targetId, String targetName,
                              String approverType, Long approverId) {
        ApprovalDO approval = new ApprovalDO();
        approval.setApplyType(applyType); approval.setApplicantId(StpUtil.getLoginIdAsLong());
        approval.setTargetType(targetType); approval.setTargetId(targetId);
        approval.setTargetName(targetName); approval.setApproverType(approverType);
        approval.setApproverId(approverId); approval.setStatus("PENDING");
        approval.setApplyTime(LocalDateTime.now());
        approvalMapper.insert(approval);
        return approval;
    }

    public Page<ApprovalDO> pendingList(Long userId, int current, int size) {
        return approvalMapper.selectPage(new Page<>(current, size),
                new LambdaQueryWrapper<ApprovalDO>().eq(ApprovalDO::getApproverId, userId)
                        .eq(ApprovalDO::getStatus, "PENDING").orderByDesc(ApprovalDO::getApplyTime));
    }

    public Page<ApprovalDO> myList(int current, int size) {
        return approvalMapper.selectPage(new Page<>(current, size),
                new LambdaQueryWrapper<ApprovalDO>().eq(ApprovalDO::getApplicantId, StpUtil.getLoginIdAsLong())
                        .orderByDesc(ApprovalDO::getApplyTime));
    }

    @Transactional
    public void approve(Long approvalId, String rejectReason) {
        ApprovalDO approval = approvalMapper.selectById(approvalId);
        if (approval == null || !"PENDING".equals(approval.getStatus())) {
            throw new RuntimeException("审批单不存在或已处理");
        }
        if (rejectReason != null && !rejectReason.isBlank()) {
            approval.setStatus("REJECTED");
            approval.setRejectReason(rejectReason);
        } else {
            approval.setStatus("APPROVED");
        }
        approval.setApproveTime(LocalDateTime.now());
        approvalMapper.updateById(approval);

        AuditLogDO log = new AuditLogDO();
        log.setUserId(StpUtil.getLoginIdAsLong());
        log.setAction(approval.getStatus());
        log.setTargetType("APPROVAL");
        log.setTargetId(approvalId);
        log.setCreateTime(LocalDateTime.now());
        auditLogMapper.insert(log);
    }
}