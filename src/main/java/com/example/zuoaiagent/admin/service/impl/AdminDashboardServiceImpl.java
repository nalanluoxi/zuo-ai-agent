package com.example.zuoaiagent.admin.service.impl;

import com.example.zuoaiagent.admin.service.AdminDashboardService;
import com.example.zuoaiagent.admin.service.RagTraceService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

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
            stats.put("activeUsers", activeUsers != null ? activeUsers : 0);
            
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
            
            // 总反馈数
            String totalSql = "SELECT COUNT(*) as total FROM t_feedback";
            Integer totalCount = jdbcTemplate.queryForObject(totalSql, Integer.class);
            feedback.put("totalFeedback", totalCount != null ? totalCount : 0);
            
            // 满意度分布
            String ratingSql = "SELECT rating, COUNT(*) as count FROM t_feedback WHERE rating IS NOT NULL GROUP BY rating";
            List<Map<String, Object>> ratings = jdbcTemplate.queryForList(ratingSql);
            feedback.put("ratingDistribution", ratings);
            
            // 常见问题（反馈最多的类别）
            String categorySql = "SELECT category, COUNT(*) as count FROM t_feedback " +
                               "WHERE category IS NOT NULL GROUP BY category ORDER BY count DESC LIMIT 5";
            List<Map<String, Object>> topCategories = jdbcTemplate.queryForList(categorySql);
            feedback.put("topCategories", topCategories);
            
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
    public Map<String, Object> getTokenTrend(int days) {
        try {
            String sql = "SELECT " +
                "DATE(created_at) as date, " +
                "COALESCE(SUM(input_tokens), 0) as input_tokens, " +
                "COALESCE(SUM(output_tokens), 0) as output_tokens " +
                "FROM t_token_usage " +
                "WHERE created_at >= CURRENT_DATE - (? || ' days')::INTERVAL " +
                "GROUP BY DATE(created_at) " +
                "ORDER BY date ASC";
            
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, days);
            List<Map<String, Object>> trendData = new ArrayList<>();
            
            for (Map<String, Object> row : rows) {
                long input = ((Number) row.get("input_tokens")).longValue();
                long output = ((Number) row.get("output_tokens")).longValue();
                Map<String, Object> trend = new LinkedHashMap<>();
                trend.put("date", row.get("date"));
                trend.put("inputTokens", input);
                trend.put("outputTokens", output);
                trend.put("totalTokens", input + output);
                trendData.add(trend);
            }
            
            return Map.of("days", days, "trendData", trendData);
        } catch (Exception e) {
            log.error("查询 Token 趋势失败：", e);
            return Map.of("days", days, "trendData", List.of());
        }
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
    public Map<String, Object> getUserActivityTrend(int days) {
        try {
            String sql = "SELECT " +
                "DATE(created_at) as date, " +
                "COUNT(DISTINCT user_id) as active_users " +
                "FROM t_conversation " +
                "WHERE created_at >= CURRENT_DATE - (? || ' days')::INTERVAL " +
                "GROUP BY DATE(created_at) " +
                "ORDER BY date ASC";
            
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, days);
            List<Map<String, Object>> trendData = new ArrayList<>();
            
            for (Map<String, Object> row : rows) {
                Map<String, Object> trend = new LinkedHashMap<>();
                trend.put("date", row.get("date"));
                trend.put("activeUsers", ((Number) row.get("active_users")).longValue());
                trendData.add(trend);
            }
            
            return Map.of("days", days, "trendData", trendData);
        } catch (Exception e) {
            log.error("查询用户活跃度趋势失败：", e);
            return Map.of("days", days, "trendData", List.of());
        }
    }

    @Override
    public Map<String, Object> getTopActiveUsers(int limit) {
        try {
            String sql = "SELECT " +
                "u.id as user_id, u.username, " +
                "COUNT(c.id) as conversation_count, " +
                "MAX(c.created_at) as last_active_at " +
                "FROM t_user u " +
                "JOIN t_conversation c ON u.id = c.user_id " +
                "WHERE u.deleted = 0 " +
                "GROUP BY u.id, u.username " +
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
                "u.id as user_id, u.username, " +
                "COALESCE(SUM(t.input_tokens), 0) as input_tokens, " +
                "COALESCE(SUM(t.output_tokens), 0) as output_tokens, " +
                "COALESCE(SUM(t.total_tokens), 0) as total_tokens " +
                "FROM t_user u " +
                "LEFT JOIN t_token_usage t ON u.id = t.user_id " +
                "WHERE u.deleted = 0 " +
                "GROUP BY u.id, u.username " +
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
    public Map<String, Object> getStageLatency(int limit) {
        try {
            String sql = "SELECT " +
                "node_type, node_name, " +
                "AVG(duration_ms) as avg_duration, " +
                "MAX(duration_ms) as max_duration, " +
                "MIN(duration_ms) as min_duration, " +
                "COUNT(*) as count " +
                "FROM t_rag_trace_node " +
                "WHERE status = 'SUCCESS' AND deleted = 0 " +
                "GROUP BY node_type, node_name " +
                "ORDER BY avg_duration DESC";
            
            List<Map<String, Object>> stages = jdbcTemplate.queryForList(sql);
            
            // 获取每个阶段的 TopN 慢请求
            for (Map<String, Object> stage : stages) {
                String nodeType = (String) stage.get("node_type");
                String topNSql = "SELECT " +
                    "trace_id, duration_ms, create_time " +
                    "FROM t_rag_trace_node " +
                    "WHERE node_type = ? AND status = 'SUCCESS' AND deleted = 0 " +
                    "ORDER BY duration_ms DESC " +
                    "LIMIT ?";
                
                stage.put("topSlowRequests", jdbcTemplate.queryForList(topNSql, nodeType, limit));
                
                // 添加中文显示名
                String displayName = switch (nodeType) {
                    case "REWRITE" -> "查询改写";
                    case "CLASSIFY" -> "意图分类";
                    case "RETRIEVE" -> "文档检索";
                    case "RERANK" -> "结果精排";
                    case "PROMPT" -> "Prompt组装";
                    default -> nodeType;
                };
                stage.put("stageDisplayName", displayName);
            }
            
            return Map.of("stages", stages);
        } catch (Exception e) {
            log.error("查询阶段耗时统计失败：", e);
            return Map.of("stages", List.of());
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
