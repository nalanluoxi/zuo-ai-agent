package com.example.zuoaiagent.dashboard.service;

import java.time.LocalDate;
import java.util.Map;

/**
 * Dashboard 服务接口
 * 支持按时间范围（1天/7天/月/自定义）查询
 * 所有统计按 userId 过滤，统一口径
 */
public interface DashboardService {

    /**
     * 看板总览：返回当天和当月的 token + 消息数
     * @param userId 当前登录用户 ID（从认证上下文获取）
     */
    Map<String, Object> getOverview(Long userId);

    /**
     * Token 消耗趋势（按天拆分，无数据的天补 0）
     * @param userId 当前登录用户 ID
     */
    Map<String, Object> getTokenTrend(Long userId, int days, LocalDate startDate, LocalDate endDate);

    /**
     * 消息频次趋势（按天拆分，无数据的天补 0）
     * 通过 conversation_id 关联 t_conversation.user_id 过滤
     * @param userId 当前登录用户 ID
     */
    Map<String, Object> getMessageTrend(Long userId, int days, LocalDate startDate, LocalDate endDate);

    /**
     * 检索耗时趋势（按天拆分，无数据的天补 0）
     * @param userId 当前登录用户 ID
     */
    Map<String, Object> getRetrievalTrend(Long userId, int days, LocalDate startDate, LocalDate endDate);

    /**
     * Top 知识库使用排名
     * @param userId 当前登录用户 ID
     */
    Map<String, Object> getTopKnowledgeBases(Long userId, int days, LocalDate startDate, LocalDate endDate, int topN);
}
