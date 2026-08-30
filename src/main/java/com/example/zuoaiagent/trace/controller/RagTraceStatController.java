package com.example.zuoaiagent.trace.controller;

import cn.hutool.core.collection.CollUtil;
import com.example.zuoaiagent.trace.model.vo.*;
import com.example.zuoaiagent.trace.service.RagTraceStatService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/dashboard/trace")
@RequiredArgsConstructor
public class RagTraceStatController {

    private final RagTraceStatService statService;

    @GetMapping("/overview")
    public DashboardOverviewVO overview(
            @RequestParam(defaultValue = "GLOBAL") String dimension,
            @RequestParam(required = false) Long tenantId,
            @RequestParam(required = false) Long userId) {
        return statService.getOverview(dimension, tenantId, userId);
    }

    @GetMapping("/node-duration")
    public List<NodeDurationVO> nodeDuration(
            @RequestParam(defaultValue = "GLOBAL") String dimension,
            @RequestParam(required = false) Long tenantId,
            @RequestParam(required = false) Long userId) {
        return statService.getNodeDuration(dimension, tenantId, userId);
    }

    @GetMapping("/error-trend")
    public List<ErrorTrendVO> errorTrend(
            @RequestParam(defaultValue = "GLOBAL") String dimension,
            @RequestParam(required = false) Long tenantId,
            @RequestParam(required = false) Long userId) {
        return statService.getErrorTrend(dimension, tenantId, userId);
    }

    @GetMapping("/call-volume")
    public List<CallVolumeVO> callVolume(
            @RequestParam(defaultValue = "GLOBAL") String dimension,
            @RequestParam(required = false) Long tenantId,
            @RequestParam(required = false) Long userId) {
        return statService.getCallVolume(dimension, tenantId, userId);
    }

    @GetMapping("/token-cost")
    public TokenCostVO tokenCost(
            @RequestParam(defaultValue = "GLOBAL") String dimension,
            @RequestParam(required = false) Long tenantId,
            @RequestParam(required = false) Long userId) {
        return statService.getTokenCost(dimension, tenantId, userId);
    }

    @GetMapping("/token-trend")
    public List<TokenTrendVO> tokenTrend(
            @RequestParam(defaultValue = "GLOBAL") String dimension,
            @RequestParam(required = false) Long tenantId,
            @RequestParam(required = false) Long userId) {
        return statService.getTokenTrend(dimension, tenantId, userId);
    }

    @GetMapping("/personal-overview")
    public Map<String, Object> personalOverview(@RequestParam Long userId) {
        return statService.getPersonalOverview(userId);
    }

    @GetMapping("/personal-trend")
    public List<Map<String, Object>> personalTrend(@RequestParam Long userId, @RequestParam(defaultValue = "day") String period) {
        return statService.getPersonalTrend(period, userId);
    }

    @GetMapping("/admin-overview")
    public Map<String, Object> adminOverview() {
        return statService.getAdminOverview();
    }

    @GetMapping("/admin-trend")
    public List<Map<String, Object>> adminTrend(@RequestParam(defaultValue = "day") String period) {
        return statService.getAdminTrend(period);
    }

    @GetMapping("/admin-user-ranking")
    public List<Map<String, Object>> userRanking(@RequestParam(defaultValue = "7") int days) {
        return statService.getUserRanking(days);
    }

    @GetMapping("/admin-kb-stats")
    public List<Map<String, Object>> kbStats() {
        return statService.getKbStats();
    }

    @GetMapping("/{traceId}")
    public Map<String, Object> traceDetail(@PathVariable String traceId) {
        return statService.getTraceDetail(traceId);
    }

    @GetMapping("/full")
    public Map<String, Object> fullDashboard(
            @RequestParam(defaultValue = "GLOBAL") String dimension,
            @RequestParam(required = false) Long tenantId,
            @RequestParam(required = false) Long userId) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("overview", statService.getOverview(dimension, tenantId, userId));
        result.put("nodeDuration", statService.getNodeDuration(dimension, tenantId, userId));
        result.put("errorTrend", statService.getErrorTrend(dimension, tenantId, userId));
        result.put("callVolume", statService.getCallVolume(dimension, tenantId, userId));
        return result;
    }
}