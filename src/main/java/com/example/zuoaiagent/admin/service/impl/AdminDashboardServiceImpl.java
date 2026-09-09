package com.example.zuoaiagent.admin.service.impl;

import com.example.zuoaiagent.admin.service.AdminDashboardService;
import com.example.zuoaiagent.admin.service.RagTraceService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;

/**
 * P29：管理看板服务实现
 */
@Service
@RequiredArgsConstructor
public class AdminDashboardServiceImpl implements AdminDashboardService {

    private static final Logger log = LoggerFactory.getLogger(AdminDashboardServiceImpl.class);
    private final JdbcTemplate jdbcTemplate;

    @Override
    public Map<String, Object> getSystemStats() {
        try {
            Map<String, Object> stats = new LinkedHashMap<>();
            
            // 用户总数
            String userCountSql = "SELECT COUNT(*) as total FROM t_user WHERE deleted = 0";
            Integer userCount = jdbcTemplate.queryForObject(userCountSql, Integer.class);
            stats.put("totalUsers", userCount != null ? userCount : 0);
            
            // 活跃用户数（最近 7 天有过操作）
            String activeUserSql = "SELECT COUNT(DISTINCT user_id) as active FROM t_conversation WHERE created_at >= NOW() - INTERVAL '7 days'";
            Integer activeUsers = jdbcTemplate.queryForObject(activeUserSql, Integer.class);
            stats.put("activeUsers7Days", activeUsers != null ? activeUsers : 0);
            
            // 租户总数
            String tenantCountSql = "SELECT COUNT(*) as total FROM t_tenant WHERE deleted = 0";
            Integer tenantCount = jdbcTemplate.queryForObject(tenantCountSql, Integer.class);
            stats.put("totalTenants", tenantCount != null ? tenantCount : 0);
            
            // 对话总数
            String conversationCountSql = "SELECT COUNT(*) as total FROM t_conversation";
            Integer conversationCount = jdbcTemplate.queryForObject(conversationCountSql, Integer.class);
            stats.put("totalConversations", conversationCount != null ? conversationCount : 0);
            
            // 知识库总数
            String kbCountSql = "SELECT COUNT(*) as total FROM t_knowledge_base WHERE deleted = 0";
            Integer kbCount = jdbcTemplate.queryForObject(kbCountSql, Integer.class);
            stats.put("totalKnowledgeBases", kbCount != null ? kbCount : 0);
            
            return stats;
        } catch (Exception e) {
            log.error("查询系统统计失败：", e);
            return Map.of(
                "totalUsers", 0,
                "activeUsers", 0,
                "totalTenants", 0,
                "totalConversations", 0,
                "totalKnowledgeBases", 0
            );
        }
    }

    @Override
    public Map<String, Object> getRagStats() {
        try {
            Map<String, Object> stats = new LinkedHashMap<>();
            
            List<Map<String, Object>> stages = new ArrayList<>();
            
            // 检索阶段统计
            String retrievalSql = "SELECT " +
                                "COUNT(*) as total_count, " +
                                "SUM(CASE WHEN success = 1 THEN 1 ELSE 0 END) as success_count, " +
                                "AVG(duration_ms) as avg_duration, " +
                                "AVG(cost) as avg_cost " +
                                "FROM t_rag_retrieval_log";
            List<Map<String, Object>> retrievalStats = jdbcTemplate.queryForList(retrievalSql);
            if (!retrievalStats.isEmpty()) {
                Map<String, Object> retrieval = new LinkedHashMap<>(retrievalStats.get(0));
                retrieval.put("stage", "Retrieval");
                stages.add(retrieval);
            }
            
            // 生成阶段统计
            String generationSql = "SELECT " +
                                 "COUNT(*) as total_count, " +
                                 "SUM(CASE WHEN success = 1 THEN 1 ELSE 0 END) as success_count, " +
                                 "AVG(duration_ms) as avg_duration, " +
                                 "AVG(cost) as avg_cost " +
                                 "FROM t_rag_generation_log";
            List<Map<String, Object>> generationStats = jdbcTemplate.queryForList(generationSql);
            if (!generationStats.isEmpty()) {
                Map<String, Object> generation = new LinkedHashMap<>(generationStats.get(0));
                generation.put("stage", "Generation");
                stages.add(generation);
            }
            
            stats.put("stages", stages);
            stats.put("stageCount", stages.size());
            
            return stats;
        } catch (Exception e) {
            log.error("查询 RAG 统计失败：", e);
            return Map.of("stages", List.of(), "stageCount", 0);
        }
    }

    @Override
    public Map<String, Object> getRagStatsTrend(int days) {
        try {
            String sql = "SELECT " +
                        "DATE(created_at) as date, " +
                        "stage, " +
                        "COUNT(*) as total_count, " +
                        "SUM(CASE WHEN success = 1 THEN 1 ELSE 0 END) as success_count, " +
                        "AVG(duration_ms) as avg_duration " +
                        "FROM t_rag_log " +
                        "WHERE created_at >= CURRENT_DATE - (? || ' days')::INTERVAL " +
                        "GROUP BY DATE(created_at), stage " +
                        "ORDER BY date ASC";
            
            List<Map<String, Object>> trendData = jdbcTemplate.queryForList(sql, days);
            
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("days", days);
            result.put("trendData", trendData);
            result.put("count", trendData.size());
            
            return result;
        } catch (Exception e) {
            log.error("查询 RAG 趋势失败：", e);
            return Map.of("days", days, "trendData", List.of(), "count", 0);
        }
    }

    @Override
    public Map<String, Object> getUserFeedback() {
        try {
            Map<String, Object> feedback = new LinkedHashMap<>();
            
            // 总反馈数（点赞+点踩）
            String totalSql = "SELECT COUNT(*) as total FROM t_message_feedback";
            Integer totalCount = jdbcTemplate.queryForObject(totalSql, Integer.class);
            feedback.put("totalFeedback", totalCount != null ? totalCount : 0);
            
            // 满意度分布（按 feedback_type 分组：1=点赞, 0=点踩）
            String ratingSql = "SELECT " +
                "CASE WHEN feedback_type = 1 THEN '点赞' ELSE '点踩' END as rating, " +
                "COUNT(*) as count " +
                "FROM t_message_feedback " +
                "GROUP BY feedback_type";
            List<Map<String, Object>> ratings = jdbcTemplate.queryForList(ratingSql);
            feedback.put("ratingDistribution", ratings);
            
            // 最近反馈（按用户分组）
            String userFeedbackSql = "SELECT " +
                "u.id as user_id, u.username, u.nickname, " +
                "COUNT(*) as feedback_count, " +
                "SUM(CASE WHEN f.feedback_type = 1 THEN 1 ELSE 0 END) as like_count, " +
                "SUM(CASE WHEN f.feedback_type = 0 THEN 1 ELSE 0 END) as dislike_count " +
                "FROM t_message_feedback f " +
                "JOIN t_user u ON f.user_id = u.id " +
                "GROUP BY u.id, u.username, u.nickname " +
                "ORDER BY feedback_count DESC LIMIT 5";
            List<Map<String, Object>> topUsers = jdbcTemplate.queryForList(userFeedbackSql);
            feedback.put("topCategories", topUsers);
            
            return feedback;
        } catch (Exception e) {
            log.error("查询用户反馈失败：", e);
            return Map.of(
                "totalFeedback", 0,
                "ratingDistribution", List.of(),
                "topCategories", List.of()
            );
        }
    }

    @Override
    public Map<String, Object> getAdminDashboardOverview() {
        try {
            Map<String, Object> overview = new LinkedHashMap<>();
            overview.put("systemStats", getSystemStats());
            overview.put("ragStats", getRagStats());
            overview.put("userFeedback", getUserFeedback());
            overview.put("recentErrors", getRecentErrors(5));
            
            return overview;
        } catch (Exception e) {
            log.error("查询管理看板总览失败：", e);
            return Map.of(
                "systemStats", Map.of(),
                "ragStats", Map.of(),
                "userFeedback", Map.of(),
                "recentErrors", List.of()
            );
        }
    }

    @Override
    public Map<String, Object> getRecentErrors(int limit) {
        try {
            String sql = "SELECT id, error_type, error_message, stack_trace, context, created_at " +
                        "FROM t_error_log " +
                        "ORDER BY created_at DESC " +
                        "LIMIT ?";
            
            List<Map<String, Object>> errors = jdbcTemplate.queryForList(sql, limit);
            
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("errors", errors);
            result.put("count", errors.size());
            
            return result;
        } catch (Exception e) {
            log.error("查询最近错误失败：", e);
            return Map.of("errors", List.of(), "count", 0);
        }
    }

    @Override
    public Map<String, Object> getTokenStats() {
        try {
            String sql = "SELECT " +
                "COALESCE(SUM(input_tokens), 0) as total_input, " +
                "COALESCE(SUM(output_tokens), 0) as total_output, " +
                "COUNT(DISTINCT DATE(created_at)) as days " +
                "FROM t_token_usage";
            
            Map<String, Object> result = jdbcTemplate.queryForMap(sql);
            long totalInput = ((Number) result.get("total_input")).longValue();
            long totalOutput = ((Number) result.get("total_output")).longValue();
            long days = ((Number) result.get("days")).longValue();
            
            Map<String, Object> stats = new LinkedHashMap<>();
            stats.put("totalInputTokens", totalInput);
            stats.put("totalOutputTokens", totalOutput);
            stats.put("totalTokens", totalInput + totalOutput);
            stats.put("avgDailyTokens", days > 0 ? (totalInput + totalOutput) / days : 0);
            stats.put("cost", String.format("%.4f", 
                (totalInput / 1000.0) * 0.0005 + (totalOutput / 1000.0) * 0.0015));
            
            return stats;
        } catch (Exception e) {
            log.error("查询 Token 统计失败：", e);
            return Map.of(
                "totalInputTokens", 0L,
                "totalOutputTokens", 0L,
                "totalTokens", 0L,
                "avgDailyTokens", 0L,
                "cost", "0.0000"
            );
        }
    }

    @Override
    public Map<String, Object> getTokenTrend(LocalDate start, LocalDate end, Long userId) {
        try {
            boolean isToday = start.equals(end) && start.equals(LocalDate.now());
            if (isToday) {
                return getTokenTrendByHour(start, userId);
            } else {
                return getTokenTrendByDay(start, end, userId);
            }
        } catch (Exception e) {
            log.error("查询 Token 趋势失败：", e);
            return Map.of("trendData", List.of());
        }
    }

    private Map<String, Object> getTokenTrendByHour(LocalDate date, Long userId) {
        String sql = "SELECT " +
            "EXTRACT(HOUR FROM created_at) as hour, " +
            "COALESCE(SUM(input_tokens), 0) as input_tokens, " +
            "COALESCE(SUM(output_tokens), 0) as output_tokens " +
            "FROM t_token_usage " +
            "WHERE DATE(created_at) = ?::date" +
            (userId != null ? " AND user_id = ?" : "") +
            " GROUP BY EXTRACT(HOUR FROM created_at) ORDER BY hour ASC";

        List<Object> params = new ArrayList<>();
        params.add(date.format(java.time.format.DateTimeFormatter.ISO_DATE));
        if (userId != null) params.add(userId);

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, params.toArray());

        // 补全 24 小时数据
        Map<Integer, long[]> dataMap = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            int hour = ((Number) row.get("hour")).intValue();
            long input = ((Number) row.get("input_tokens")).longValue();
            long output = ((Number) row.get("output_tokens")).longValue();
            dataMap.put(hour, new long[]{input, output});
        }

        List<Map<String, Object>> trendData = new ArrayList<>();
        for (int h = 0; h < 24; h++) {
            long[] values = dataMap.getOrDefault(h, new long[]{0, 0});
            Map<String, Object> trend = new LinkedHashMap<>();
            trend.put("hour", h);
            trend.put("date", String.format("%02d:00", h));
            trend.put("inputTokens", values[0]);
            trend.put("outputTokens", values[1]);
            trend.put("totalTokens", values[0] + values[1]);
            trendData.add(trend);
        }

        return Map.of("date", date.toString(), "granularity", "hour", "trendData", trendData);
    }

    private Map<String, Object> getTokenTrendByDay(LocalDate start, LocalDate end, Long userId) {
        String sql = "SELECT " +
            "DATE(created_at) as date, " +
            "COALESCE(SUM(input_tokens), 0) as input_tokens, " +
            "COALESCE(SUM(output_tokens), 0) as output_tokens " +
            "FROM t_token_usage " +
            "WHERE DATE(created_at) >= ?::date AND DATE(created_at) <= ?::date" +
            (userId != null ? " AND user_id = ?" : "") +
            " GROUP BY DATE(created_at) ORDER BY date ASC";

        List<Object> params = new ArrayList<>();
        params.add(start.format(java.time.format.DateTimeFormatter.ISO_DATE));
        params.add(end.format(java.time.format.DateTimeFormatter.ISO_DATE));
        if (userId != null) params.add(userId);

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, params.toArray());

        // 补全日期范围内所有天的数据
        Map<String, long[]> dataMap = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String dateStr = row.get("date").toString();
            long input = ((Number) row.get("input_tokens")).longValue();
            long output = ((Number) row.get("output_tokens")).longValue();
            dataMap.put(dateStr, new long[]{input, output});
        }

        List<Map<String, Object>> trendData = new ArrayList<>();
        long totalDays = java.time.temporal.ChronoUnit.DAYS.between(start, end) + 1;
        for (int i = 0; i < totalDays; i++) {
            LocalDate d = start.plusDays(i);
            String dateStr = d.toString();
            long[] values = dataMap.getOrDefault(dateStr, new long[]{0, 0});
            Map<String, Object> trend = new LinkedHashMap<>();
            trend.put("date", dateStr);
            trend.put("inputTokens", values[0]);
            trend.put("outputTokens", values[1]);
            trend.put("totalTokens", values[0] + values[1]);
            trendData.add(trend);
        }

        return Map.of("startDate", start.toString(), "endDate", end.toString(),
                      "days", (int) totalDays, "granularity", "day", "trendData", trendData);
    }

    @Override
    public Map<String, Object> getUserActivity() {
        try {
            Map<String, Object> result = new LinkedHashMap<>();
            
            // 总用户数
            Integer totalUsers = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_user WHERE deleted = 0", Integer.class);
            result.put("totalUsers", totalUsers != null ? totalUsers : 0);
            
            // 7天活跃用户
            Integer active7 = jdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT user_id) FROM t_conversation " +
                "WHERE created_at >= CURRENT_DATE - INTERVAL '7 days'", Integer.class);
            result.put("activeUsers7Days", active7 != null ? active7 : 0);
            
            // 30天活跃用户
            Integer active30 = jdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT user_id) FROM t_conversation " +
                "WHERE created_at >= CURRENT_DATE - INTERVAL '30 days'", Integer.class);
            result.put("activeUsers30Days", active30 != null ? active30 : 0);
            
            // DAU 平均
            Double dauAvg = jdbcTemplate.queryForObject(
                "SELECT AVG(cnt) FROM (" +
                "SELECT DATE(created_at) as d, COUNT(DISTINCT user_id) as cnt " +
                "FROM t_conversation GROUP BY DATE(created_at)) t", Double.class);
            result.put("dauAvg", dauAvg != null ? Math.round(dauAvg * 100.0) / 100.0 : 0.0);
            
            return result;
        } catch (Exception e) {
            log.error("查询用户活跃度失败：", e);
            return Map.of(
                "totalUsers", 0,
                "activeUsers7Days", 0,
                "activeUsers30Days", 0,
                "dauAvg", 0.0
            );
        }
    }

    @Override
    public Map<String, Object> getUserActivityTrend(LocalDate start, LocalDate end, Long userId) {
        try {
            boolean isToday = start.equals(end) && start.equals(LocalDate.now());
            if (isToday) {
                return getUserActivityTrendByHour(start, userId);
            } else {
                return getUserActivityTrendByDay(start, end, userId);
            }
        } catch (Exception e) {
            log.error("查询用户活跃度趋势失败：", e);
            return Map.of("trendData", List.of());
        }
    }

    private Map<String, Object> getUserActivityTrendByHour(LocalDate date, Long userId) {
        String sql = "SELECT " +
            "EXTRACT(HOUR FROM created_at) as hour, " +
            "COUNT(DISTINCT user_id) as active_users " +
            "FROM t_token_usage " +
            "WHERE DATE(created_at) = ?::date AND user_id IS NOT NULL" +
            (userId != null ? " AND user_id = ?" : "") +
            " GROUP BY EXTRACT(HOUR FROM created_at) ORDER BY hour ASC";

        List<Object> params = new ArrayList<>();
        params.add(date.format(java.time.format.DateTimeFormatter.ISO_DATE));
        if (userId != null) params.add(userId);

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, params.toArray());

        // 补全 24 小时数据
        Map<Integer, Long> dataMap = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            int hour = ((Number) row.get("hour")).intValue();
            long active = ((Number) row.get("active_users")).longValue();
            dataMap.put(hour, active);
        }

        List<Map<String, Object>> trendData = new ArrayList<>();
        for (int h = 0; h < 24; h++) {
            long active = dataMap.getOrDefault(h, 0L);
            Map<String, Object> trend = new LinkedHashMap<>();
            trend.put("hour", h);
            trend.put("date", String.format("%02d:00", h));
            trend.put("activeUsers", active);
            trendData.add(trend);
        }

        return Map.of("date", date.toString(), "granularity", "hour", "trendData", trendData);
    }

    private Map<String, Object> getUserActivityTrendByDay(LocalDate start, LocalDate end, Long userId) {
        String sql = "SELECT " +
            "DATE(created_at) as date, " +
            "COUNT(DISTINCT user_id) as active_users " +
            "FROM t_token_usage " +
            "WHERE DATE(created_at) >= ?::date AND DATE(created_at) <= ?::date " +
            "AND user_id IS NOT NULL" +
            (userId != null ? " AND user_id = ?" : "") +
            " GROUP BY DATE(created_at) ORDER BY date ASC";

        List<Object> params = new ArrayList<>();
        params.add(start.format(java.time.format.DateTimeFormatter.ISO_DATE));
        params.add(end.format(java.time.format.DateTimeFormatter.ISO_DATE));
        if (userId != null) params.add(userId);

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, params.toArray());

        // 补全日期范围内所有天的数据
        Map<String, Long> dataMap = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String dateStr = row.get("date").toString();
            long active = ((Number) row.get("active_users")).longValue();
            dataMap.put(dateStr, active);
        }

        List<Map<String, Object>> trendData = new ArrayList<>();
        long totalDays = java.time.temporal.ChronoUnit.DAYS.between(start, end) + 1;
        for (int i = 0; i < totalDays; i++) {
            LocalDate d = start.plusDays(i);
            String dateStr = d.toString();
            long active = dataMap.getOrDefault(dateStr, 0L);
            Map<String, Object> trend = new LinkedHashMap<>();
            trend.put("date", dateStr);
            trend.put("activeUsers", active);
            trendData.add(trend);
        }

        return Map.of("startDate", start.toString(), "endDate", end.toString(),
                      "days", (int) totalDays, "granularity", "day", "trendData", trendData);
    }

    @Override
    public Map<String, Object> getTopActiveUsers(int limit) {
        try {
            // 改用 t_token_usage 表统计活跃用户（因为 t_conversation.user_id 可能为 NULL）
            String sql = "SELECT " +
                "u.id as user_id, u.username, u.nickname, " +
                "COUNT(DISTINCT t.conversation_id) as conversation_count, " +
                "MAX(t.created_at) as last_active_at " +
                "FROM t_user u " +
                "JOIN t_token_usage t ON u.id = t.user_id " +
                "WHERE u.deleted = 0 AND t.user_id IS NOT NULL " +
                "GROUP BY u.id, u.username, u.nickname " +
                "ORDER BY conversation_count DESC " +
                "LIMIT ?";
            
            List<Map<String, Object>> users = jdbcTemplate.queryForList(sql, limit);
            return Map.of("limit", limit, "users", users);
        } catch (Exception e) {
            log.error("查询活跃用户 TopN 失败：", e);
            return Map.of("limit", limit, "users", List.of());
        }
    }

    @Override
    public Map<String, Object> getTopTokenUsers(int limit) {
        try {
            String sql = "SELECT " +
                "u.id as user_id, u.username, u.nickname, " +
                "COALESCE(SUM(t.input_tokens), 0) as input_tokens, " +
                "COALESCE(SUM(t.output_tokens), 0) as output_tokens, " +
                "COALESCE(SUM(t.total_tokens), 0) as total_tokens " +
                "FROM t_user u " +
                "LEFT JOIN t_token_usage t ON u.id = t.user_id " +
                "WHERE u.deleted = 0 " +
                "GROUP BY u.id, u.username, u.nickname " +
                "ORDER BY total_tokens DESC " +
                "LIMIT ?";
            
            List<Map<String, Object>> users = jdbcTemplate.queryForList(sql, limit);
            return Map.of("limit", limit, "users", users);
        } catch (Exception e) {
            log.error("查询 Token 消耗 TopN 失败：", e);
            return Map.of("limit", limit, "users", List.of());
        }
    }

    @Override
    public Map<String, Object> getE2ELatency() {
        try {
            String sql = "SELECT " +
                "AVG(duration_ms) as avg_duration, " +
                "MAX(duration_ms) as max_duration, " +
                "MIN(duration_ms) as min_duration, " +
                "PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY duration_ms) as p50_duration, " +
                "PERCENTILE_CONT(0.95) WITHIN GROUP (ORDER BY duration_ms) as p95_duration, " +
                "COUNT(*) as total_requests " +
                "FROM t_rag_trace_run " +
                "WHERE status = 'SUCCESS' AND deleted = 0";
            
            Map<String, Object> result = jdbcTemplate.queryForMap(sql);
            return Map.of(
                "avgDurationMs", result.get("avg_duration") != null ? ((Number) result.get("avg_duration")).longValue() : 0,
                "maxDurationMs", result.get("max_duration") != null ? ((Number) result.get("max_duration")).longValue() : 0,
                "minDurationMs", result.get("min_duration") != null ? ((Number) result.get("min_duration")).longValue() : 0,
                "p50DurationMs", result.get("p50_duration") != null ? ((Number) result.get("p50_duration")).longValue() : 0,
                "p95DurationMs", result.get("p95_duration") != null ? ((Number) result.get("p95_duration")).longValue() : 0,
                "totalRequests", result.get("total_requests") != null ? ((Number) result.get("total_requests")).longValue() : 0
            );
        } catch (Exception e) {
            log.error("查询端到端耗时失败：", e);
            return Map.of(
                "avgDurationMs", 0L,
                "maxDurationMs", 0L,
                "minDurationMs", 0L,
                "p50DurationMs", 0L,
                "p95DurationMs", 0L,
                "totalRequests", 0L
            );
        }
    }

    @Override
    public Map<String, Object> getStageLatency(int limit, LocalDate start, LocalDate end, Long userId) {
        try {
            boolean isToday = start.equals(end) && start.equals(LocalDate.now());

            // 阶段命名与个人看板统一
            String[] allStages = {"REWRITE", "HYDE", "CLASSIFY", "RETRIEVE", "RERANK", "PROMPT", "LLM"};
            Map<String, String> stageNames = Map.of(
                "REWRITE", "提示词改写",
                "HYDE", "HyDE 假设生成",
                "CLASSIFY", "意图识别",
                "RETRIEVE", "检索",
                "RERANK", "Rerank 重排序",
                "PROMPT", "Prompt 组装",
                "LLM", "增强生成"
            );

            if (isToday) {
                return getStageLatencyByHour(start, allStages, stageNames, userId);
            } else {
                return getStageLatencyByDay(start, end, allStages, stageNames, userId);
            }
        } catch (Exception e) {
            log.error("查询阶段耗时统计失败：", e);
            return Map.of("stages", List.of());
        }
    }

    private Map<String, Object> getStageLatencyByHour(LocalDate date, String[] allStages, Map<String, String> stageNames, Long userId) {
        // userId 非空时按用户过滤（trace_node → trace_run → conversation）
        String userJoin = userId != null
                ? " JOIN t_rag_trace_run r ON n.trace_id = r.trace_id JOIN t_conversation c ON r.conversation_id = c.id "
                : "";
        String userFilter = userId != null ? " AND c.user_id = ? " : "";
        String sql = "SELECT " +
            "n.node_type, " +
            "EXTRACT(HOUR FROM n.create_time) as hour, " +
            "AVG(n.duration_ms) as avg_duration, " +
            "MAX(n.duration_ms) as max_duration, " +
            "MIN(n.duration_ms) as min_duration, " +
            "COUNT(*) as count " +
            "FROM t_rag_trace_node n " + userJoin +
            "WHERE n.status = 'SUCCESS' AND n.deleted = 0 " +
            "AND DATE(n.create_time) = ?::date " + userFilter +
            "GROUP BY n.node_type, EXTRACT(HOUR FROM n.create_time) " +
            "ORDER BY hour ASC, n.node_type ASC";

        List<Map<String, Object>> rows = userId != null
                ? jdbcTemplate.queryForList(sql, date, userId)
                : jdbcTemplate.queryForList(sql, date);
        
        List<Map<String, Object>> stageTrends = new ArrayList<>();
        for (String stage : allStages) {
            Map<String, Object> stageData = new LinkedHashMap<>();
            stageData.put("nodeType", stage);
            stageData.put("displayName", stageNames.get(stage));
            
            Map<Integer, Map<String, Object>> hourDataMap = new LinkedHashMap<>();
            for (Map<String, Object> row : rows) {
                if (stage.equals(row.get("node_type"))) {
                    int hour = ((Number) row.get("hour")).intValue();
                    hourDataMap.put(hour, row);
                }
            }
            
            List<Map<String, Object>> trendData = new ArrayList<>();
            long sumAvg = 0, sumMax = 0, sumMin = 0, sumCount = 0;
            boolean hasData = false;
            
            for (int h = 0; h < 24; h++) {
                Map<String, Object> trend = new LinkedHashMap<>();
                trend.put("label", String.format("%02d:00", h));
                
                Map<String, Object> rowData = hourDataMap.get(h);
                if (rowData != null) {
                    long avg = ((Number) rowData.get("avg_duration")).longValue();
                    long max = ((Number) rowData.get("max_duration")).longValue();
                    long min = ((Number) rowData.get("min_duration")).longValue();
                    long count = ((Number) rowData.get("count")).longValue();
                    
                    trend.put("avgDurationMs", avg);
                    trend.put("maxDurationMs", max);
                    trend.put("minDurationMs", min);
                    trend.put("count", count);
                    
                    sumAvg += avg * count;
                    sumMax = Math.max(sumMax, max);
                    sumMin = hasData ? Math.min(sumMin, min) : min;
                    sumCount += count;
                    hasData = true;
                } else {
                    trend.put("avgDurationMs", 0L);
                    trend.put("maxDurationMs", 0L);
                    trend.put("minDurationMs", 0L);
                    trend.put("count", 0L);
                }
                trendData.add(trend);
            }
            
            stageData.put("trendData", trendData);
            stageData.put("granularity", "hour");
            stageData.put("avgDurationMs", sumCount > 0 ? sumAvg / sumCount : 0L);
            stageData.put("maxDurationMs", sumMax);
            stageData.put("minDurationMs", sumMin);
            stageData.put("totalCalls", sumCount);
            
            stageTrends.add(stageData);
        }
        
        return Map.of("date", date.toString(), "granularity", "hour", "stages", stageTrends);
    }

    private Map<String, Object> getStageLatencyByDay(LocalDate start, LocalDate end, String[] allStages, Map<String, String> stageNames, Long userId) {
        String userJoin = userId != null
                ? " JOIN t_rag_trace_run r ON n.trace_id = r.trace_id JOIN t_conversation c ON r.conversation_id = c.id "
                : "";
        String userFilter = userId != null ? " AND c.user_id = ? " : "";
        String sql = "SELECT " +
            "n.node_type, DATE(n.create_time) as date, " +
            "AVG(n.duration_ms) as avg_duration, " +
            "MAX(n.duration_ms) as max_duration, " +
            "MIN(n.duration_ms) as min_duration, " +
            "COUNT(*) as count " +
            "FROM t_rag_trace_node n " + userJoin +
            "WHERE n.status = 'SUCCESS' AND n.deleted = 0 " +
            "AND DATE(n.create_time) >= ?::date AND DATE(n.create_time) <= ?::date " + userFilter +
            "GROUP BY n.node_type, DATE(n.create_time) " +
            "ORDER BY date ASC, n.node_type ASC";

        List<Map<String, Object>> rows = userId != null
                ? jdbcTemplate.queryForList(sql, start, end, userId)
                : jdbcTemplate.queryForList(sql, start, end);
        
        List<Map<String, Object>> stageTrends = new ArrayList<>();
        long totalDays = java.time.temporal.ChronoUnit.DAYS.between(start, end) + 1;
        
        for (String stage : allStages) {
            Map<String, Object> stageData = new LinkedHashMap<>();
            stageData.put("nodeType", stage);
            stageData.put("displayName", stageNames.get(stage));
            
            Map<String, Map<String, Object>> dateDataMap = new LinkedHashMap<>();
            for (Map<String, Object> row : rows) {
                if (stage.equals(row.get("node_type"))) {
                    String dateStr = row.get("date").toString();
                    dateDataMap.put(dateStr, row);
                }
            }
            
            List<Map<String, Object>> trendData = new ArrayList<>();
            long sumAvg = 0, sumMax = 0, sumMin = 0, sumCount = 0;
            boolean hasData = false;
            
            for (int i = 0; i < totalDays; i++) {
                LocalDate d = start.plusDays(i);
                String dateStr = d.toString();
                
                Map<String, Object> trend = new LinkedHashMap<>();
                trend.put("label", dateStr);
                
                Map<String, Object> rowData = dateDataMap.get(dateStr);
                if (rowData != null) {
                    long avg = ((Number) rowData.get("avg_duration")).longValue();
                    long max = ((Number) rowData.get("max_duration")).longValue();
                    long min = ((Number) rowData.get("min_duration")).longValue();
                    long count = ((Number) rowData.get("count")).longValue();
                    
                    trend.put("avgDurationMs", avg);
                    trend.put("maxDurationMs", max);
                    trend.put("minDurationMs", min);
                    trend.put("count", count);
                    
                    sumAvg += avg * count;
                    sumMax = Math.max(sumMax, max);
                    sumMin = hasData ? Math.min(sumMin, min) : min;
                    sumCount += count;
                    hasData = true;
                } else {
                    trend.put("avgDurationMs", 0L);
                    trend.put("maxDurationMs", 0L);
                    trend.put("minDurationMs", 0L);
                    trend.put("count", 0L);
                }
                trendData.add(trend);
            }
            
            stageData.put("trendData", trendData);
            stageData.put("granularity", "day");
            stageData.put("avgDurationMs", sumCount > 0 ? sumAvg / sumCount : 0L);
            stageData.put("maxDurationMs", sumMax);
            stageData.put("minDurationMs", sumMin);
            stageData.put("totalCalls", sumCount);
            
            stageTrends.add(stageData);
        }
        
        return Map.of("startDate", start.toString(), "endDate", end.toString(),
                      "days", (int) totalDays, "granularity", "day", "stages", stageTrends);
    }

    @Override
    public List<Map<String, Object>> getUserList() {
        try {
            String sql = "SELECT id, username, nickname FROM t_user WHERE deleted = 0 ORDER BY id";
            return jdbcTemplate.queryForList(sql);
        } catch (Exception e) {
            log.error("查询用户列表失败：", e);
            return List.of();
        }
    }

    @Override
    public Map<String, Object> getKbStats(Long userId) {
        try {
            // userId 非空时只统计该用户名下（owner_id）的知识库
            String kbScope = userId != null ? " AND owner_id = ? " : "";
            String sql = "SELECT " +
                    "(SELECT COUNT(*) FROM t_knowledge_base WHERE deleted = 0" + kbScope + ") as kb_count, " +
                    "(SELECT COUNT(*) FROM t_knowledge_document d WHERE d.deleted = 0 AND d.kb_id IN " +
                    "   (SELECT id FROM t_knowledge_base WHERE deleted = 0" + kbScope + ")) as doc_count, " +
                    "(SELECT COALESCE(SUM(d.file_size), 0) FROM t_knowledge_document d WHERE d.deleted = 0 AND d.kb_id IN " +
                    "   (SELECT id FROM t_knowledge_base WHERE deleted = 0" + kbScope + ")) as total_size_bytes, " +
                    "(SELECT COALESCE(SUM(l.chunks_count), 0) FROM t_ingestion_log l WHERE l.stage = 'chunk' AND l.kb_id IN " +
                    "   (SELECT id FROM t_knowledge_base WHERE deleted = 0" + kbScope + ")) as total_chunks";

            Map<String, Object> row = userId != null
                    ? jdbcTemplate.queryForMap(sql, userId, userId, userId, userId)
                    : jdbcTemplate.queryForMap(sql);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("kbCount", ((Number) row.getOrDefault("kb_count", 0L)).longValue());
            result.put("docCount", ((Number) row.getOrDefault("doc_count", 0L)).longValue());
            result.put("totalChunks", ((Number) row.getOrDefault("total_chunks", 0L)).longValue());
            long totalSizeBytes = ((Number) row.getOrDefault("total_size_bytes", 0L)).longValue();
            result.put("totalSizeBytes", totalSizeBytes);
            result.put("totalSizeMb", Math.round(totalSizeBytes / 1024.0 / 1024.0 * 100.0) / 100.0);
            return result;
        } catch (Exception e) {
            log.error("查询知识库统计失败：", e);
            return Map.of("kbCount", 0L, "docCount", 0L, "totalChunks", 0L, "totalSizeBytes", 0L, "totalSizeMb", 0.0);
        }
    }

    @Override
    public Map<String, Object> getTraceDetails(String keyword, LocalDate start, LocalDate end, int page, int pageSize, Long userId, String grayTag) {        try {
            // 构建查询条件
            StringBuilder whereClause = new StringBuilder();
            List<Object> params = new ArrayList<>();

            whereClause.append("r.deleted = 0 ");
            whereClause.append("AND DATE(r.create_time) >= ?::date ");
            whereClause.append("AND DATE(r.create_time) <= ?::date ");
            params.add(start);
            params.add(end);

            // 按用户过滤（全链路详情用户下拉）
            if (userId != null) {
                whereClause.append("AND c.user_id = ? ");
                params.add(userId);
            }

            // 灰度标签过滤
            if (grayTag != null && !grayTag.trim().isEmpty()) {
                whereClause.append("AND r.gray_tag = ? ");
                params.add(grayTag);
            }

            if (keyword != null && !keyword.trim().isEmpty()) {
                whereClause.append("AND (u.username ILIKE ? OR u.nickname ILIKE ?) ");
                String kw = "%" + keyword.trim() + "%";
                params.add(kw);
                params.add(kw);
            }
            
            // 查询总数
            String countSql = "SELECT COUNT(*) FROM t_rag_trace_run r " +
                             "LEFT JOIN t_conversation c ON r.conversation_id = c.id " +
                             "LEFT JOIN t_user u ON c.user_id = u.id " +
                             "WHERE " + whereClause;
            Integer total = jdbcTemplate.queryForObject(countSql, Integer.class, params.toArray());
            
            // 查询分页数据
            String sql = "SELECT r.id, r.trace_id, r.conversation_id, r.original_prompt, " +
                        "r.status, r.duration_ms, r.create_time, r.gray_tag, " +
                        "c.user_id, u.username, u.nickname " +
                        "FROM t_rag_trace_run r " +
                        "LEFT JOIN t_conversation c ON r.conversation_id = c.id " +
                        "LEFT JOIN t_user u ON c.user_id = u.id " +
                        "WHERE " + whereClause +
                        "ORDER BY r.create_time DESC " +
                        "LIMIT ? OFFSET ?";
            
            params.add(pageSize);
            params.add((page - 1) * pageSize);
            
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, params.toArray());
            
            // 构建响应
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("total", total != null ? total : 0);
            result.put("page", page);
            result.put("pageSize", pageSize);
            result.put("data", rows);
            
            return result;
        } catch (Exception e) {
            log.error("查询链路详情失败：", e);
            return Map.of("total", 0, "page", page, "pageSize", pageSize, "data", List.of());
        }
    }

    @Override
    public Map<String, Object> getTraceDetailByTraceId(String traceId) {
        try {
            // 查询 run 信息
            String runSql = "SELECT r.id, r.trace_id, r.conversation_id, r.original_prompt, " +
                           "r.status, r.duration_ms, r.create_time, r.gray_tag, " +
                           "c.user_id, u.username, u.nickname " +
                           "FROM t_rag_trace_run r " +
                           "LEFT JOIN t_conversation c ON r.conversation_id = c.id " +
                           "LEFT JOIN t_user u ON c.user_id = u.id " +
                           "WHERE r.trace_id = ? AND r.deleted = 0";
            
            List<Map<String, Object>> runs = jdbcTemplate.queryForList(runSql, traceId);
            if (runs.isEmpty()) {
                return Map.of("found", false, "message", "链路不存在");
            }
            
            Map<String, Object> run = runs.get(0);
            
            // 查询节点信息
            String nodeSql = "SELECT id, node_id, node_name, node_type, status, duration_ms, " +
                            "input_data, output_data, error_message, prompt_tokens, completion_tokens " +
                            "FROM t_rag_trace_node " +
                            "WHERE trace_id = ? AND deleted = 0 " +
                            "ORDER BY start_time ASC";
            
            List<Map<String, Object>> nodes = jdbcTemplate.queryForList(nodeSql, traceId);
            run.put("nodes", nodes);
            
            return Map.of("found", true, "data", run);
        } catch (Exception e) {
            log.error("查询链路详情失败：", e);
            return Map.of("found", false, "message", "查询失败");
        }
    }
}

/**
 * P29：链路追踪服务实现
 */
@Service
@RequiredArgsConstructor
class RagTraceServiceImpl implements RagTraceService {

    private static final Logger log = LoggerFactory.getLogger(RagTraceServiceImpl.class);
    private final JdbcTemplate jdbcTemplate;

    @Override
    public Map<String, Object> getCompleteTrace(String inputId) {
        try {
            Map<String, Object> trace = new LinkedHashMap<>();
            trace.put("inputId", inputId);
            
            // 查询原始输入
            String inputSql = "SELECT id, user_id, content, model, created_at FROM t_user_input WHERE id = ?";
            List<Map<String, Object>> inputs = jdbcTemplate.queryForList(inputSql, inputId);
            
            if (inputs.isEmpty()) {
                return Map.of("found", false, "message", "输入记录不存在");
            }
            
            trace.put("input", inputs.get(0));
            
            // 查询检索过程
            String retrievalSql = "SELECT id, input_id, knowledge_base_id, relevance_score, duration_ms FROM t_rag_retrieval_log WHERE input_id = ? ORDER BY created_at ASC";
            List<Map<String, Object>> retrievals = jdbcTemplate.queryForList(retrievalSql, inputId);
            trace.put("retrievals", retrievals);
            
            // 查询生成过程
            String generationSql = "SELECT id, input_id, prompt_tokens, completion_tokens, cost, duration_ms FROM t_rag_generation_log WHERE input_id = ? ORDER BY created_at ASC";
            List<Map<String, Object>> generations = jdbcTemplate.queryForList(generationSql, inputId);
            trace.put("generations", generations);
            
            // 查询最终响应
            String responseSql = "SELECT id, input_id, output_content, feedback_score FROM t_user_response WHERE input_id = ? LIMIT 1";
            List<Map<String, Object>> responses = jdbcTemplate.queryForList(responseSql, inputId);
            if (!responses.isEmpty()) {
                trace.put("response", responses.get(0));
            }
            
            trace.put("found", true);
            
            return trace;
        } catch (Exception e) {
            log.error("查询链路追踪失败：", e);
            return Map.of("found", false, "error", e.getMessage());
        }
    }

    @Override
    public Map<String, Object> getTraceStats() {
        try {
            Map<String, Object> stats = new LinkedHashMap<>();
            
            // 按模型统计
            String modelSql = "SELECT model, COUNT(*) as count FROM t_user_input GROUP BY model";
            List<Map<String, Object>> modelStats = jdbcTemplate.queryForList(modelSql);
            stats.put("modelDistribution", modelStats);
            
            // 按输入类型统计
            String typeSql = "SELECT input_type, COUNT(*) as count FROM t_user_input GROUP BY input_type";
            List<Map<String, Object>> typeStats = jdbcTemplate.queryForList(typeSql);
            stats.put("typeDistribution", typeStats);
            
            // 性能统计
            String perfSql = "SELECT AVG(duration_ms) as avg_duration, MAX(duration_ms) as max_duration, MIN(duration_ms) as min_duration FROM t_user_input";
            List<Map<String, Object>> perfStats = jdbcTemplate.queryForList(perfSql);
            if (!perfStats.isEmpty()) {
                stats.put("performanceStats", perfStats.get(0));
            }
            
            return stats;
        } catch (Exception e) {
            log.error("查询链路统计失败：", e);
            return Map.of(
                "modelDistribution", List.of(),
                "typeDistribution", List.of(),
                "performanceStats", Map.of()
            );
        }
    }
}
