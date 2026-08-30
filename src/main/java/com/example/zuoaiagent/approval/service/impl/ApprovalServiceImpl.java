package com.example.zuoaiagent.approval.service.impl;

import com.example.zuoaiagent.approval.service.ApprovalService;
import com.example.zuoaiagent.exception.BusinessException;
import com.example.zuoaiagent.exception.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * P28：审批服务实现
 */
@Service
@RequiredArgsConstructor
public class ApprovalServiceImpl implements ApprovalService {

    private static final Logger log = LoggerFactory.getLogger(ApprovalServiceImpl.class);
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public Map<String, Object> listApprovals(int page, int size, String status) {
        try {
            StringBuilder sql = new StringBuilder(
                "SELECT id, title, description, applicant_id, approver_id, status, " +
                "approval_remark, rejection_reason, created_at, updated_at " +
                "FROM t_approval WHERE 1=1"
            );
            List<Object> params = new ArrayList<>();
            
            if (status != null && !status.trim().isEmpty()) {
                sql.append(" AND status = ?");
                params.add(status);
            }
            
            sql.append(" ORDER BY created_at DESC LIMIT ? OFFSET ?");
            params.add(size);
            params.add((page - 1) * size);
            
            List<Map<String, Object>> content = jdbcTemplate.queryForList(sql.toString(), params.toArray());
            
            // 获取总数
            StringBuilder countSql = new StringBuilder("SELECT COUNT(*) as total FROM t_approval WHERE 1=1");
            List<Object> countParams = new ArrayList<>();
            
            if (status != null && !status.trim().isEmpty()) {
                countSql.append(" AND status = ?");
                countParams.add(status);
            }
            
            Integer total = jdbcTemplate.queryForObject(countSql.toString(), Integer.class, countParams.toArray());
            if (total == null) total = 0;
            
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("content", content);
            result.put("totalElements", total);
            result.put("totalPages", (total + size - 1) / size);
            result.put("currentPage", page);
            result.put("pageSize", size);
            
            return result;
        } catch (Exception e) {
            log.error("查询审批列表失败：", e);
            return Map.of(
                "content", List.of(),
                "totalElements", 0,
                "totalPages", 0,
                "currentPage", page,
                "pageSize", size
            );
        }
    }

    @Override
    public Map<String, Object> getApprovalDetail(Long approvalId) {
        try {
            String sql = "SELECT id, title, description, applicant_id, approver_id, status, " +
                        "approval_remark, rejection_reason, approval_data, created_at, updated_at " +
                        "FROM t_approval WHERE id = ?";
            
            List<Map<String, Object>> results = jdbcTemplate.queryForList(sql, approvalId);
            
            if (results.isEmpty()) {
                throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "审批工单不存在");
            }
            
            return results.get(0);
        } catch (Exception e) {
            if (e instanceof BusinessException) throw e;
            log.error("查询审批详情失败：", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "查询审批详情失败");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> createApproval(String title, String description, String applicantId, Map<String, Object> data) {
        try {
            Long approvalId = System.currentTimeMillis();
            
            String approvalData;
            try {
                approvalData = objectMapper.writeValueAsString(data);
            } catch (Exception e) {
                approvalData = data.toString();
            }
            
            String sql = "INSERT INTO t_approval (id, title, description, applicant_id, status, approval_data, created_at, updated_at) " +
                        "VALUES (?, ?, ?, ?, ?, ?, NOW(), NOW())";
            
            jdbcTemplate.update(sql, approvalId, title, description, applicantId, "PENDING", approvalData);
            
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("id", approvalId);
            result.put("title", title);
            result.put("description", description);
            result.put("applicantId", applicantId);
            result.put("status", "PENDING");
            result.put("createdAt", LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME));
            
            log.info("创建审批工单 - approvalId: {}, title: {}", approvalId, title);
            
            return result;
        } catch (Exception e) {
            log.error("创建审批失败：", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "创建审批失败：" + e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> approveWithConditions(Long approvalId, String approverId, String remark, Map<String, Object> conditions) {
        try {
            // 验证审批工单存在
            Map<String, Object> approval = getApprovalDetail(approvalId);
            
            // 检查状态
            String status = (String) approval.get("status");
            if (!"PENDING".equals(status)) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "工单状态为 " + status + "，无法审批");
            }
            
            // 存储审批数据（包括条件）
            String conditionData;
            try {
                conditionData = objectMapper.writeValueAsString(conditions);
            } catch (Exception e) {
                conditionData = conditions.toString();
            }
            
            String sql = "UPDATE t_approval SET status = ?, approver_id = ?, approval_remark = ?, approval_data = ?, updated_at = NOW() " +
                        "WHERE id = ?";
            
            jdbcTemplate.update(sql, "APPROVED", approverId, remark, conditionData, approvalId);
            
            log.info("条件审批通过 - approvalId: {}, approverId: {}", approvalId, approverId);
            
            return getApprovalDetail(approvalId);
        } catch (Exception e) {
            if (e instanceof BusinessException) throw e;
            log.error("审批失败：", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "审批失败：" + e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> rejectApproval(Long approvalId, String approverId, String rejectionReason) {
        try {
            // 验证审批工单存在
            Map<String, Object> approval = getApprovalDetail(approvalId);
            
            // 检查状态
            String status = (String) approval.get("status");
            if (!"PENDING".equals(status)) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "工单状态为 " + status + "，无法拒绝");
            }
            
            String sql = "UPDATE t_approval SET status = ?, approver_id = ?, rejection_reason = ?, updated_at = NOW() " +
                        "WHERE id = ?";
            
            jdbcTemplate.update(sql, "REJECTED", approverId, rejectionReason, approvalId);
            
            log.info("审批拒绝 - approvalId: {}, approverId: {}", approvalId, approverId);
            
            return getApprovalDetail(approvalId);
        } catch (Exception e) {
            if (e instanceof BusinessException) throw e;
            log.error("拒绝审批失败：", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "拒绝审批失败：" + e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> updateApprovalStatus(Long approvalId, String status) {
        try {
            // 验证状态值
            if (!status.matches("^(PENDING|APPROVED|REJECTED)$")) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "状态只能是 PENDING/APPROVED/REJECTED");
            }
            
            String sql = "UPDATE t_approval SET status = ?, updated_at = NOW() WHERE id = ?";
            
            int rows = jdbcTemplate.update(sql, status, approvalId);
            
            if (rows == 0) {
                throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "审批工单不存在");
            }
            
            return getApprovalDetail(approvalId);
        } catch (Exception e) {
            if (e instanceof BusinessException) throw e;
            log.error("更新审批状态失败：", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "更新审批状态失败");
        }
    }

    @Override
    public Map<String, Object> getPendingApprovals(String userId, int page, int size) {
        try {
            String sql = "SELECT id, title, description, applicant_id, status, " +
                        "created_at, updated_at " +
                        "FROM t_approval " +
                        "WHERE status = 'PENDING' " +
                        "ORDER BY created_at DESC LIMIT ? OFFSET ?";
            
            List<Map<String, Object>> content = jdbcTemplate.queryForList(sql, size, (page - 1) * size);
            
            // 获取总数
            String countSql = "SELECT COUNT(*) as total FROM t_approval WHERE status = 'PENDING'";
            Integer total = jdbcTemplate.queryForObject(countSql, Integer.class);
            if (total == null) total = 0;
            
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("content", content);
            result.put("totalElements", total);
            result.put("totalPages", (total + size - 1) / size);
            result.put("currentPage", page);
            result.put("pageSize", size);
            result.put("userId", userId);
            
            return result;
        } catch (Exception e) {
            log.error("查询待审批列表失败：", e);
            return Map.of(
                "content", List.of(),
                "totalElements", 0,
                "totalPages", 0,
                "currentPage", page,
                "pageSize", size
            );
        }
    }
}
