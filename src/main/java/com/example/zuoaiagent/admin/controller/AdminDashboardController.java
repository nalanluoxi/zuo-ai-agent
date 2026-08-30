package com.example.zuoaiagent.admin.controller;

import com.example.zuoaiagent.common.BaseResponse;
import com.example.zuoaiagent.common.ResultUtils;
import com.example.zuoaiagent.exception.BusinessException;
import com.example.zuoaiagent.exception.ErrorCode;
import com.example.zuoaiagent.admin.service.AdminDashboardService;
import com.example.zuoaiagent.admin.service.RagTraceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * P29：管理看板 API
 * 功能：系统统计、RAG 管道统计、用户反馈、完整链路追踪
 */
@RestController
@RequestMapping("/admin/dashboard")
@RequiredArgsConstructor
public class AdminDashboardController {

    private final AdminDashboardService adminDashboardService;
    private final RagTraceService ragTraceService;

    /**
     * P29：获取系统统计信息
     * @return 用户总数、活跃用户、租户数、对话数等
     */
    @GetMapping("/system-stats")
    public BaseResponse<Map<String, Object>> getSystemStats() {
        return ResultUtils.success(adminDashboardService.getSystemStats());
    }

    /**
     * P29：获取 RAG 管道统计（按阶段）
     * @return 各个阶段的成功率、平均耗时、成本等
     */
    @GetMapping("/rag-stats")
    public BaseResponse<Map<String, Object>> getRagStats() {
        return ResultUtils.success(adminDashboardService.getRagStats());
    }

    /**
     * P29：获取 RAG 管道统计（详细版，分时间段）
     * @param days 统计天数（7/30）
     */
    @GetMapping("/rag-stats-trend")
    public BaseResponse<Map<String, Object>> getRagStatsTrend(
            @RequestParam(defaultValue = "7") int days) {
        
        if (days != 7 && days != 30) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "天数只能选择 7 或 30");
        }
        
        return ResultUtils.success(adminDashboardService.getRagStatsTrend(days));
    }

    /**
     * P29：获取用户反馈聚合统计
     * @return 反馈总数、满意度分布、常见问题等
     */
    @GetMapping("/user-feedback")
    public BaseResponse<Map<String, Object>> getUserFeedback() {
        return ResultUtils.success(adminDashboardService.getUserFeedback());
    }

    /**
     * P29：获取管理看板总览（聚合数据）
     * @return 系统统计、RAG 统计、反馈、最近事件
     */
    @GetMapping("/overview")
    public BaseResponse<Map<String, Object>> getAdminDashboardOverview() {
        return ResultUtils.success(adminDashboardService.getAdminDashboardOverview());
    }

    /**
     * P29：完整链路追踪（根据原始输入 ID）
     * @param inputId 原始用户输入的 ID
     * @return 从输入到最终响应的完整链路
     */
    @GetMapping("/trace/{inputId}")
    public BaseResponse<Map<String, Object>> getCompleteTrace(@PathVariable String inputId) {
        if (inputId == null || inputId.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "inputId 不能为空");
        }
        
        return ResultUtils.success(ragTraceService.getCompleteTrace(inputId));
    }

    /**
     * P29：获取链路追踪统计（按输入类型、模型等）
     * @return 追踪分布、性能统计
     */
    @GetMapping("/trace-stats")
    public BaseResponse<Map<String, Object>> getTraceStats() {
        return ResultUtils.success(ragTraceService.getTraceStats());
    }

    /**
     * P29：获取最近异常和错误
     * @param limit 返回条数，默认 20
     */
    @GetMapping("/recent-errors")
    public BaseResponse<Map<String, Object>> getRecentErrors(
            @RequestParam(defaultValue = "20") int limit) {
        
        if (limit < 1 || limit > 100) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "limit 必须在 1-100 之间");
        }
        
        return ResultUtils.success(adminDashboardService.getRecentErrors(limit));
    }

    /**
     * 获取 Token 消耗统计
     */
    @GetMapping("/token-stats")
    public BaseResponse<Map<String, Object>> getTokenStats() {
        return ResultUtils.success(adminDashboardService.getTokenStats());
    }

    /**
     * 获取 Token 消耗趋势
     * @param days 统计天数（7/30）
     */
    @GetMapping("/token-trend")
    public BaseResponse<Map<String, Object>> getTokenTrend(
            @RequestParam(defaultValue = "7") int days) {
        return ResultUtils.success(adminDashboardService.getTokenTrend(days));
    }

    /**
     * 获取用户活跃度统计
     */
    @GetMapping("/user-activity")
    public BaseResponse<Map<String, Object>> getUserActivity() {
        return ResultUtils.success(adminDashboardService.getUserActivity());
    }

    /**
     * 获取用户活跃度趋势
     * @param days 统计天数（7/30）
     */
    @GetMapping("/user-activity-trend")
    public BaseResponse<Map<String, Object>> getUserActivityTrend(
            @RequestParam(defaultValue = "7") int days) {
        return ResultUtils.success(adminDashboardService.getUserActivityTrend(days));
    }

    /**
     * 获取活跃用户 TopN
     * @param limit TopN 数量，默认 10
     */
    @GetMapping("/top-active-users")
    public BaseResponse<Map<String, Object>> getTopActiveUsers(
            @RequestParam(defaultValue = "10") int limit) {
        return ResultUtils.success(adminDashboardService.getTopActiveUsers(limit));
    }

    /**
     * 获取 Token 消耗 TopN 用户
     * @param limit TopN 数量，默认 10
     */
    @GetMapping("/top-token-users")
    public BaseResponse<Map<String, Object>> getTopTokenUsers(
            @RequestParam(defaultValue = "10") int limit) {
        return ResultUtils.success(adminDashboardService.getTopTokenUsers(limit));
    }

    /**
     * 获取端到端耗时统计
     */
    @GetMapping("/e2e-latency")
    public BaseResponse<Map<String, Object>> getE2ELatency() {
        return ResultUtils.success(adminDashboardService.getE2ELatency());
    }

    /**
     * 获取全链路阶段耗时统计
     * @param limit 每个阶段的 TopN 数量，默认 10
     */
    @GetMapping("/stage-latency")
    public BaseResponse<Map<String, Object>> getStageLatency(
            @RequestParam(defaultValue = "10") int limit) {
        return ResultUtils.success(adminDashboardService.getStageLatency(limit));
    }
}
