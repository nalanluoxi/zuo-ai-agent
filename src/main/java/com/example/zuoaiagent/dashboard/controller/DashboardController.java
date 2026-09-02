package com.example.zuoaiagent.dashboard.controller;

import com.example.zuoaiagent.common.BaseResponse;
import com.example.zuoaiagent.common.ResultUtils;
import com.example.zuoaiagent.config.TenantContextHolder;
import com.example.zuoaiagent.dashboard.service.DashboardService;
import com.example.zuoaiagent.exception.BusinessException;
import com.example.zuoaiagent.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Map;

/**
 * Dashboard 看板 API
 *
 * 统一时间范围参数：
 *   period = day | week | month | custom（默认 week）
 *   startDate / endDate = yyyy-MM-dd（period=custom 时必填）
 *
 * 所有统计按当前登录用户 userId 过滤，保证口径统一
 */
@RestController
@RequestMapping("/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private static final Logger log = LoggerFactory.getLogger(DashboardController.class);

    private final DashboardService dashboardService;

    @GetMapping("/overview")
    public BaseResponse<Map<String, Object>> getOverview() {
        Long userId = TenantContextHolder.getUserId();
        log.debug("Dashboard overview, userId={}", userId);
        return ResultUtils.success(dashboardService.getOverview(userId));
    }

    @GetMapping("/token-trend")
    public BaseResponse<Map<String, Object>> getTokenTrend(
            @RequestParam(defaultValue = "week") String period,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String usageType) {

        DateRange range = parseRange(period, startDate, endDate);
        Long userId = TenantContextHolder.getUserId();
        return ResultUtils.success(dashboardService.getTokenTrend(userId, range.days(), range.start(), range.end(), usageType));
    }

    @GetMapping("/message-trend")
    public BaseResponse<Map<String, Object>> getMessageTrend(
            @RequestParam(defaultValue = "week") String period,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {

        DateRange range = parseRange(period, startDate, endDate);
        Long userId = TenantContextHolder.getUserId();
        return ResultUtils.success(dashboardService.getMessageTrend(userId, range.days(), range.start(), range.end()));
    }

    @GetMapping("/retrieval-trend")
    public BaseResponse<Map<String, Object>> getRetrievalTrend(
            @RequestParam(defaultValue = "week") String period,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {

        DateRange range = parseRange(period, startDate, endDate);
        Long userId = TenantContextHolder.getUserId();
        return ResultUtils.success(dashboardService.getRetrievalTrend(userId, range.days(), range.start(), range.end()));
    }

    @GetMapping("/top-knowledge-bases")
    public BaseResponse<Map<String, Object>> getTopKnowledgeBases(
            @RequestParam(defaultValue = "week") String period,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(defaultValue = "5") int topN) {

        if (topN < 1 || topN > 100) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "topN 必须在 1-100 之间");
        }

        DateRange range = parseRange(period, startDate, endDate);
        Long userId = TenantContextHolder.getUserId();
        return ResultUtils.success(dashboardService.getTopKnowledgeBases(
            userId, range.days(), range.start(), range.end(), topN));
    }

    @GetMapping("/ingestion/overview")
    public BaseResponse<Map<String, Object>> getIngestionOverview(
            @RequestParam(required = false) Long kbId) {
        Long userId = TenantContextHolder.getUserId();
        log.debug("Dashboard ingestion overview, userId={}, kbId={}", userId, kbId);
        return ResultUtils.success(dashboardService.getIngestionOverview(userId, kbId));
    }

    @GetMapping("/ingestion/duration-stats")
    public BaseResponse<Map<String, Object>> getIngestionDurationStats(
            @RequestParam(required = false) Long kbId,
            @RequestParam(defaultValue = "week") String period,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {

        DateRange range = parseRange(period, startDate, endDate);
        Long userId = TenantContextHolder.getUserId();
        return ResultUtils.success(dashboardService.getIngestionDurationStats(
                userId, kbId, range.days(), range.start(), range.end()));
    }

    @GetMapping("/ingestion/trend")
    public BaseResponse<Map<String, Object>> getIngestionTrend(
            @RequestParam(defaultValue = "week") String period,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {

        DateRange range = parseRange(period, startDate, endDate);
        Long userId = TenantContextHolder.getUserId();
        return ResultUtils.success(dashboardService.getIngestionTrend(userId, range.days(), range.start(), range.end()));
    }

    private static final java.util.Set<String> RAG_STAGES =
            java.util.Set.of("REWRITE", "CLASSIFY", "RETRIEVE", "RERANK", "LLM");

    @GetMapping("/rag-stage-trend")
    public BaseResponse<Map<String, Object>> getRagStageTrend(
            @RequestParam String stage,
            @RequestParam(defaultValue = "week") String period,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {

        if (stage == null || !RAG_STAGES.contains(stage.toUpperCase())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR,
                "stage 只能选择 REWRITE/CLASSIFY/RETRIEVE/RERANK/LLM");
        }

        DateRange range = parseRange(period, startDate, endDate);
        Long userId = TenantContextHolder.getUserId();
        return ResultUtils.success(dashboardService.getRagStageTrend(
                userId, stage.toUpperCase(), range.days(), range.start(), range.end()));
    }

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
}
