package com.example.zuoaiagent.admin.service;

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
     */
    Map<String, Object> getTokenTrend(int days);

    /**
     * 获取用户活跃度统计
     */
    Map<String, Object> getUserActivity();

    /**
     * 获取用户活跃度趋势
     */
    Map<String, Object> getUserActivityTrend(int days);

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
    Map<String, Object> getStageLatency(int limit);
}
