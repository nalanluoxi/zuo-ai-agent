package com.example.zuoaiagent.admin.controller;

import com.example.zuoaiagent.common.BaseResponse;
import com.example.zuoaiagent.common.ResultUtils;
import com.example.zuoaiagent.exception.BusinessException;
import com.example.zuoaiagent.exception.ErrorCode;
import com.example.zuoaiagent.admin.service.AdminDashboardService;
import com.example.zuoaiagent.admin.service.RagTraceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
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
    private final com.example.zuoaiagent.dashboard.service.DashboardService dashboardService;

    record DateRange(int days, LocalDate start, LocalDate end) {}

    private DateRange parseRange(String period, String startDate, String endDate) {
        LocalDate today = LocalDate.now();

        return switch (period == null ? "week" : period) {
            case "day" -> new DateRange(1, today, today);
            case "week" -> new DateRange(7, today.minusDays(6), today);
            case "month" -> {
                LocalDate monthStart = today.withDayOfMonth(1);
                LocalDate monthEnd = today.withDayOfMonth(today.lengthOfMonth());
                yield new DateRange(today.lengthOfMonth(), monthStart, monthEnd);
            }
            case "custom" -> {
                if (startDate == null || endDate == null) {
                    throw new BusinessException(ErrorCode.PARAMS_ERROR,
                        "period=custom 时 startDate 和 endDate 必填，格式 yyyy-MM-dd");
                }
                LocalDate s = parseDate(startDate);
                LocalDate e = parseDate(endDate);
                if (s.isAfter(e)) {
                    throw new BusinessException(ErrorCode.PARAMS_ERROR, "startDate 不能大于 endDate");
                }
                long days = ChronoUnit.DAYS.between(s, e) + 1;
                if (days > 365) {
                    throw new BusinessException(ErrorCode.PARAMS_ERROR, "时间范围不能超过 365 天");
                }
                yield new DateRange((int) days, s, e);
            }
            default -> throw new BusinessException(ErrorCode.PARAMS_ERROR,
                "period 只能选择 day/week/month/custom");
        };
    }

    private LocalDate parseDate(String date) {
        try {
            return LocalDate.parse(date, DateTimeFormatter.ISO_DATE);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "日期格式错误，请使用 yyyy-MM-dd");
        }
    }

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
     * @param period 时间范围：day/week/month/custom
     * @param startDate 自定义开始日期（period=custom 时必填）
     * @param endDate 自定义结束日期（period=custom 时必填）
     */
    @GetMapping("/rag-stats-trend")
    public BaseResponse<Map<String, Object>> getRagStatsTrend(
            @RequestParam(defaultValue = "week") String period,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        
        DateRange range = parseRange(period, startDate, endDate);
        return ResultUtils.success(adminDashboardService.getRagStatsTrend(range.days()));
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
     * @param period 时间范围：day/week/month/custom
     * @param startDate 自定义开始日期（period=custom 时必填）
     * @param endDate 自定义结束日期（period=custom 时必填）
     * @param userId 用户 ID（可选，用于筛选特定用户）
     */
    @GetMapping("/token-trend")
    public BaseResponse<Map<String, Object>> getTokenTrend(
            @RequestParam(defaultValue = "week") String period,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) Long userId) {
        DateRange range = parseRange(period, startDate, endDate);
        return ResultUtils.success(adminDashboardService.getTokenTrend(range.start(), range.end(), userId));
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
     * @param period 时间范围：day/week/month/custom
     * @param startDate 自定义开始日期（period=custom 时必填）
     * @param endDate 自定义结束日期（period=custom 时必填）
     * @param userId 用户 ID（可选，用于筛选特定用户）
     */
    @GetMapping("/user-activity-trend")
    public BaseResponse<Map<String, Object>> getUserActivityTrend(
            @RequestParam(defaultValue = "week") String period,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) Long userId) {
        DateRange range = parseRange(period, startDate, endDate);
        return ResultUtils.success(adminDashboardService.getUserActivityTrend(range.start(), range.end(), userId));
    }

    /**
     * 获取用户列表（用于筛选）
     * @return 用户列表（id, username, nickname）
     */
    @GetMapping("/user-list")
    public BaseResponse<Map<String, Object>> getUserList() {
        try {
            String sql = "SELECT id, username, nickname FROM t_user WHERE deleted = 0 ORDER BY id";
            List<Map<String, Object>> users = adminDashboardService.getUserList();
            return ResultUtils.success(Map.of("users", users, "count", users.size()));
        } catch (Exception e) {
            return ResultUtils.success(Map.of("users", List.of(), "count", 0));
        }
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
     * @param period 时间范围：day/week/month/custom
     * @param startDate 自定义开始日期（period=custom 时必填）
     * @param endDate 自定义结束日期（period=custom 时必填）
     */
    @GetMapping("/stage-latency")
    public BaseResponse<Map<String, Object>> getStageLatency(
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(defaultValue = "week") String period,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) Long userId) {
        DateRange range = parseRange(period, startDate, endDate);
        return ResultUtils.success(adminDashboardService.getStageLatency(limit, range.start(), range.end(), userId));
    }

    /**
     * 获取全链路详情列表（支持展开查看每个阶段的入参/出参）
     * @param keyword 用户搜索关键字（用户名或昵称）
     * @param period 时间范围：day/week/month/custom
     * @param startDate 自定义开始日期（period=custom 时必填）
     * @param endDate 自定义结束日期（period=custom 时必填）
     * @param page 页码（从1开始）
     * @param pageSize 每页大小
     * @param grayTag 灰度标签过滤（可选：BASELINE/TAG_A/TAG_B）
     */
    @GetMapping("/trace-details")
    public BaseResponse<Map<String, Object>> getTraceDetails(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "week") String period,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String grayTag) {
        DateRange range = parseRange(period, startDate, endDate);
        return ResultUtils.success(adminDashboardService.getTraceDetails(
                keyword, range.start(), range.end(), page, pageSize, userId, grayTag));
    }

    /**
     * 知识库统计（全局看板）：知识库数/文档数/分块总数/总大小
     * @param userId 可选，指定用户时只统计其名下知识库
     */
    @GetMapping("/kb-stats")
    public BaseResponse<Map<String, Object>> getKbStats(@RequestParam(required = false) Long userId) {
        return ResultUtils.success(adminDashboardService.getKbStats(userId));
    }

    /**
     * ETL 入库概览（全局看板）：userId 为空=全局
     */
    @GetMapping("/ingestion/overview")
    public BaseResponse<Map<String, Object>> getIngestionOverview(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) Long kbId) {
        return ResultUtils.success(dashboardService.getIngestionOverview(userId, kbId));
    }

    /**
     * ETL 入库耗时统计（全局看板）：userId 为空=全局
     */
    @GetMapping("/ingestion/duration-stats")
    public BaseResponse<Map<String, Object>> getIngestionDurationStats(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) Long kbId,
            @RequestParam(defaultValue = "week") String period,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        DateRange range = parseRange(period, startDate, endDate);
        return ResultUtils.success(dashboardService.getIngestionDurationStats(
                userId, kbId, range.days(), range.start(), range.end()));
    }

    /**
     * ETL 入库趋势（全局看板）：userId 为空=全局
     */
    @GetMapping("/ingestion/trend")
    public BaseResponse<Map<String, Object>> getIngestionTrend(
            @RequestParam(required = false) Long userId,
            @RequestParam(defaultValue = "week") String period,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        DateRange range = parseRange(period, startDate, endDate);
        return ResultUtils.success(dashboardService.getIngestionTrend(
                userId, range.days(), range.start(), range.end()));
    }

    /**
     * 知识库使用频率 TopN（全局看板）：userId 为空=全局
     */
    @GetMapping("/top-knowledge-bases")
    public BaseResponse<Map<String, Object>> getTopKnowledgeBases(
            @RequestParam(required = false) Long userId,
            @RequestParam(defaultValue = "week") String period,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(defaultValue = "5") int topN) {
        if (topN < 1 || topN > 100) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "topN 必须在 1-100 之间");
        }
        DateRange range = parseRange(period, startDate, endDate);
        return ResultUtils.success(dashboardService.getTopKnowledgeBases(
                userId, range.days(), range.start(), range.end(), topN));
    }

    /**
     * 获取单个链路的完整详情（包含所有节点的入参/出参）
     * @param traceId 链路 ID
     */
    @GetMapping("/trace-detail/{traceId}")
    public BaseResponse<Map<String, Object>> getTraceDetailByTraceId(@PathVariable String traceId) {
        return ResultUtils.success(adminDashboardService.getTraceDetailByTraceId(traceId));
    }
}
