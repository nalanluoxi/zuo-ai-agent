package com.example.zuoaiagent.dashboard.controller;

import com.example.zuoaiagent.common.BaseResponse;
import com.example.zuoaiagent.common.ResultUtils;
import com.example.zuoaiagent.dashboard.service.DashboardService;
import com.example.zuoaiagent.exception.BusinessException;
import com.example.zuoaiagent.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * P24：个人看板 API
 * 功能：Token 统计、命中率统计、趋势数据、TOP 知识库推荐
 */
@RestController
@RequestMapping("/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    /**
     * P24：获取当日 Token 统计信息
     * @return Token 总消耗、输入/输出分布、成本统计
     */
    @GetMapping("/token-stats")
    public BaseResponse<Map<String, Object>> getTokenStats(
            @RequestParam(required = false) String date) {
        
        LocalDate queryDate = parseDate(date);
        return ResultUtils.success(dashboardService.getTokenStats(queryDate));
    }

    /**
     * P24：获取 Token 消耗趋势（7天/30天）
     * @param days 统计天数，默认 7，可选 30
     */
    @GetMapping("/token-trend")
    public BaseResponse<Map<String, Object>> getTokenTrend(
            @RequestParam(defaultValue = "7") int days) {
        
        if (days != 7 && days != 30) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "天数只能选择 7 或 30");
        }
        
        return ResultUtils.success(dashboardService.getTokenTrend(days));
    }

    /**
     * P24：获取检索命中率统计
     * @return 命中率百分比、成功次数、总次数
     */
    @GetMapping("/retrieval-stats")
    public BaseResponse<Map<String, Object>> getRetrievalStats(
            @RequestParam(required = false) String date) {
        
        LocalDate queryDate = parseDate(date);
        return ResultUtils.success(dashboardService.getRetrievalStats(queryDate));
    }

    /**
     * P24：获取检索命中率趋势（7天/30天）
     * @param days 统计天数，默认 7，可选 30
     */
    @GetMapping("/retrieval-trend")
    public BaseResponse<Map<String, Object>> getRetrievalTrend(
            @RequestParam(defaultValue = "7") int days) {
        
        if (days != 7 && days != 30) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "天数只能选择 7 或 30");
        }
        
        return ResultUtils.success(dashboardService.getRetrievalTrend(days));
    }

    /**
     * P24：获取 TOP 5 常用知识库
     * @return 知识库列表、访问次数、文件数量
     */
    @GetMapping("/top-knowledge-bases")
    public BaseResponse<Map<String, Object>> getTopKnowledgeBases(
            @RequestParam(defaultValue = "5") int limit) {
        
        if (limit < 1 || limit > 50) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "limit 必须在 1-50 之间");
        }
        
        return ResultUtils.success(dashboardService.getTopKnowledgeBases(limit));
    }

    /**
     * P24：获取最近对话列表
     * @return 最近 10 条对话
     */
    @GetMapping("/recent-conversations")
    public BaseResponse<Map<String, Object>> getRecentConversations(
            @RequestParam(defaultValue = "10") int limit) {
        
        if (limit < 1 || limit > 50) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "limit 必须在 1-50 之间");
        }
        
        return ResultUtils.success(dashboardService.getRecentConversations(limit));
    }

    /**
     * P24：获取个人看板总览数据
     * @return Token 统计、检索命中率、TOP 知识库、最近对话
     */
    @GetMapping("/overview")
    public BaseResponse<Map<String, Object>> getDashboardOverview() {
        return ResultUtils.success(dashboardService.getDashboardOverview());
    }

    // ==================== 工具方法 ====================

    private LocalDate parseDate(String date) {
        if (date == null || date.isEmpty()) {
            return LocalDate.now();
        }
        try {
            return LocalDate.parse(date, DateTimeFormatter.ISO_DATE);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "日期格式错误，请使用 yyyy-MM-dd");
        }
    }
}
