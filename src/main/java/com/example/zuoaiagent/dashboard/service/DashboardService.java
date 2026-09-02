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
     * @param usageType 用途类型过滤（可选）：CONVERSATION / EMBEDDING / RETRIEVAL，null 表示全部
     */
    Map<String, Object> getTokenTrend(Long userId, int days, LocalDate startDate, LocalDate endDate, String usageType);

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

    /**
     * ETL 入库统计概览
     * @param userId 当前登录用户 ID
     * @param kbId 知识库 ID（可选，为空时查询当前用户全部知识库）
     */
    Map<String, Object> getIngestionOverview(Long userId, Long kbId);

    /**
     * ETL 入库耗时统计（按阶段分组）
     * @param userId 当前登录用户 ID
     * @param kbId 知识库 ID（可选，为空时查询当前用户全部知识库）
     * @param days 统计天数
     * @param startDate 开始日期
     * @param endDate 结束日期
     */
    Map<String, Object> getIngestionDurationStats(Long userId, Long kbId, int days, LocalDate startDate, LocalDate endDate);

    /**
     * ETL 入库趋势（按天/小时拆分，文档数 + 耗时）
     * @param userId 当前登录用户 ID
     * @param days 统计天数
     * @param startDate 开始日期
     * @param endDate 结束日期
     */
    Map<String, Object> getIngestionTrend(Long userId, int days, LocalDate startDate, LocalDate endDate);

    /**
     * RAG 检索流水线单阶段耗时趋势（按天/小时拆分，最大/最小/平均耗时）
     * @param userId 当前登录用户 ID
     * @param stage 阶段类型：REWRITE(提示词改写) / CLASSIFY(预编写文档-意图识别) / RETRIEVE(检索) / RERANK(重排序) / LLM(增强生成)
     * @param days 统计天数
     * @param startDate 开始日期
     * @param endDate 结束日期
     */
    Map<String, Object> getRagStageTrend(Long userId, String stage, int days, LocalDate startDate, LocalDate endDate);
}
