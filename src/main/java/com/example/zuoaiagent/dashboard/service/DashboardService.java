package com.example.zuoaiagent.dashboard.service;

import java.time.LocalDate;
import java.util.Map;

/**
 * P24：个人看板服务接口
 */
public interface DashboardService {

    /**
     * 获取指定日期的 Token 统计
     */
    Map<String, Object> getTokenStats(LocalDate date);

    /**
     * 获取 Token 消耗趋势
     */
    Map<String, Object> getTokenTrend(int days);

    /**
     * 获取指定日期的检索命中率
     */
    Map<String, Object> getRetrievalStats(LocalDate date);

    /**
     * 获取检索命中率趋势
     */
    Map<String, Object> getRetrievalTrend(int days);

    /**
     * 获取 TOP N 知识库
     */
    Map<String, Object> getTopKnowledgeBases(int limit);

    /**
     * 获取最近对话列表
     */
    Map<String, Object> getRecentConversations(int limit);

    /**
     * 获取仪表板总览
     */
    Map<String, Object> getDashboardOverview();
}
