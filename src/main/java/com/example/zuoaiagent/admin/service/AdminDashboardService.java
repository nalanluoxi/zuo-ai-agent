package com.example.zuoaiagent.admin.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * P29：管理看板服务接口
 */
public interface AdminDashboardService {

    /**
     * 获取系统统计
     */
    Map<String, Object> getSystemStats();

    /**
     * 获取 RAG 管道统计
     */
    Map<String, Object> getRagStats();

    /**
     * 获取 RAG 统计趋势
     */
    Map<String, Object> getRagStatsTrend(int days);

    /**
     * 获取用户反馈
     */
    Map<String, Object> getUserFeedback();

    /**
     * 获取管理看板总览
     */
    Map<String, Object> getAdminDashboardOverview();

    /**
     * 获取最近错误
     */
    Map<String, Object> getRecentErrors(int limit);

    /**
     * 获取 Token 消耗统计
     */
    Map<String, Object> getTokenStats();

    /**
     * 获取 Token 消耗趋势
     * @param start 开始日期（含）
     * @param end 结束日期（含）
     * @param userId 用户ID（可选，为null时查全部）
     */
    Map<String, Object> getTokenTrend(LocalDate start, LocalDate end, Long userId);

    /**
     * 获取用户活跃度统计
     */
    Map<String, Object> getUserActivity();

    /**
     * 获取用户活跃度趋势
     * @param start 开始日期（含）
     * @param end 结束日期（含）
     * @param userId 用户ID（可选，为null时查全部）
     */
    Map<String, Object> getUserActivityTrend(LocalDate start, LocalDate end, Long userId);

    /**
     * 获取活跃用户 TopN
     */
    Map<String, Object> getTopActiveUsers(int limit);

    /**
     * 获取 Token 消耗 TopN 用户
     */
    Map<String, Object> getTopTokenUsers(int limit);

    /**
     * 获取端到端耗时统计
     */
    Map<String, Object> getE2ELatency();

    /**
     * 获取全链路阶段耗时统计
     */
    Map<String, Object> getStageLatency(int limit, LocalDate start, LocalDate end, Long userId);

    /**
     * 获取全链路详情列表（支持展开查看每个阶段的入参/出参）
     * @param keyword 用户搜索关键字（用户名或昵称）
     * @param start 开始日期（含）
     * @param end 结束日期（含）
     * @param page 页码（从1开始）
     * @param pageSize 每页大小
     */
    Map<String, Object> getTraceDetails(String keyword, LocalDate start, LocalDate end, int page, int pageSize, Long userId);

    /**
     * 知识库统计（全局看板）：知识库数/文档数/分块总数/总大小
     * @param userId 可选，指定用户时只统计其名下的知识库
     */
    Map<String, Object> getKbStats(Long userId);

    /**
     * 获取单个链路的完整详情（包含所有节点）
     * @param traceId 链路 ID
     */
    Map<String, Object> getTraceDetailByTraceId(String traceId);

    /**
     * 获取用户列表（用于筛选）
     */
    List<Map<String, Object>> getUserList();
}
