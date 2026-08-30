package com.example.zuoaiagent.trace.service;

import com.example.zuoaiagent.trace.model.vo.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
public class RagTraceStatService {

    private final JdbcTemplate jdbcTemplate;

    public DashboardOverviewVO getOverview(String dimension, Long tenantId, Long userId) {
        String where = buildWhere(dimension, tenantId, userId);
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT COUNT(*) as total, " +
                "COALESCE(SUM(CASE WHEN status='SUCCESS' THEN 1 ELSE 0 END) * 100.0 / NULLIF(COUNT(*),0), 0) as success_rate, " +
                "COALESCE(AVG(duration_ms), 0) as avg_duration, " +
                "COALESCE(PERCENTILE_CONT(0.95) WITHIN GROUP (ORDER BY duration_ms), 0) as p95_duration " +
                "FROM t_rag_trace_run WHERE 1=1 " + where);
        DashboardOverviewVO vo = new DashboardOverviewVO();
        vo.setTotalCalls(((Number) row.get("total")).longValue());
        vo.setSuccessRate(((Number) row.get("success_rate")).doubleValue());
        vo.setAvgDurationMs(((Number) row.get("avg_duration")).doubleValue());
        vo.setP95DurationMs(((Number) row.get("p95_duration")).doubleValue());
        return vo;
    }

    public List<NodeDurationVO> getNodeDuration(String dimension, Long tenantId, Long userId) {
        String where = buildWhere(dimension, tenantId, userId);
        return jdbcTemplate.query(
                "SELECT n.node_type, COUNT(*) as count, AVG(n.duration_ms) as avg_duration, " +
                "PERCENTILE_CONT(0.95) WITHIN GROUP (ORDER BY n.duration_ms) as p95_duration " +
                "FROM t_rag_trace_node n JOIN t_rag_trace_run r ON n.run_id = r.id " +
                "WHERE 1=1 " + where.replace("tenant_id", "r.tenant_id").replace("user_id", "r.user_id") +
                " GROUP BY n.node_type ORDER BY avg_duration DESC",
                (rs, rowNum) -> {
                    NodeDurationVO vo = new NodeDurationVO();
                    vo.setNodeType(rs.getString("node_type"));
                    vo.setCount(rs.getLong("count"));
                    vo.setAvgDurationMs(rs.getDouble("avg_duration"));
                    vo.setP95DurationMs(rs.getDouble("p95_duration"));
                    return vo;
                });
    }

    public List<ErrorTrendVO> getErrorTrend(String dimension, Long tenantId, Long userId) {
        String where = buildWhere(dimension, tenantId, userId);
        return jdbcTemplate.query(
                "SELECT DATE_TRUNC('hour', create_time) as hour, COUNT(*) as total, " +
                "SUM(CASE WHEN status='FAIL' THEN 1 ELSE 0 END) as errors " +
                "FROM t_rag_trace_run WHERE create_time > NOW() - INTERVAL '24 hours' " + where +
                " GROUP BY hour ORDER BY hour",
                (rs, rowNum) -> {
                    ErrorTrendVO vo = new ErrorTrendVO();
                    vo.setHour(rs.getTimestamp("hour").toLocalDateTime());
                    vo.setTotal(rs.getLong("total"));
                    vo.setErrors(rs.getLong("errors"));
                    return vo;
                });
    }

    public List<CallVolumeVO> getCallVolume(String dimension, Long tenantId, Long userId) {
        String where = buildWhere(dimension, tenantId, userId);
        return jdbcTemplate.query(
                "SELECT DATE_TRUNC('hour', create_time) as hour, COUNT(*) as count " +
                "FROM t_rag_trace_run WHERE create_time > NOW() - INTERVAL '24 hours' " + where +
                " GROUP BY hour ORDER BY hour",
                (rs, rowNum) -> {
                    CallVolumeVO vo = new CallVolumeVO();
                    vo.setHour(rs.getTimestamp("hour").toLocalDateTime());
                    vo.setCount(rs.getLong("count"));
                    return vo;
                });
    }

    public TokenCostVO getTokenCost(String dimension, Long tenantId, Long userId) {
        String where = buildWhere(dimension, tenantId, userId);
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT COALESCE(SUM(n.prompt_tokens), 0) as total_prompt, " +
                "COALESCE(SUM(n.completion_tokens), 0) as total_completion, " +
                "COUNT(DISTINCT n.run_id) as call_count " +
                "FROM t_rag_trace_node n JOIN t_rag_trace_run r ON n.run_id = r.id " +
                "WHERE r.create_time > NOW() - INTERVAL '24 hours' " + where.replace("tenant_id", "r.tenant_id").replace("user_id", "r.user_id"));
        TokenCostVO vo = new TokenCostVO();
        vo.setTotalPromptTokens(((Number) row.get("total_prompt")).longValue());
        vo.setTotalCompletionTokens(((Number) row.get("total_completion")).longValue());
        vo.setCallCount(((Number) row.get("call_count")).longValue());
        vo.setEstimatedCost((vo.getTotalPromptTokens() * 0.001 + vo.getTotalCompletionTokens() * 0.002) / 1000.0);
        return vo;
    }

    public List<TokenTrendVO> getTokenTrend(String dimension, Long tenantId, Long userId) {
        String where = buildWhere(dimension, tenantId, userId);
        return jdbcTemplate.query(
                "SELECT DATE_TRUNC('hour', r.create_time) as hour, " +
                "COALESCE(SUM(n.prompt_tokens), 0) as prompt_tokens, " +
                "COALESCE(SUM(n.completion_tokens), 0) as completion_tokens " +
                "FROM t_rag_trace_node n JOIN t_rag_trace_run r ON n.run_id = r.id " +
                "WHERE r.create_time > NOW() - INTERVAL '24 hours' " + where.replace("tenant_id", "r.tenant_id").replace("user_id", "r.user_id") +
                " GROUP BY hour ORDER BY hour",
                (rs, rowNum) -> {
                    TokenTrendVO vo = new TokenTrendVO();
                    vo.setHour(rs.getTimestamp("hour").toLocalDateTime());
                    vo.setPromptTokens(rs.getLong("prompt_tokens"));
                    vo.setCompletionTokens(rs.getLong("completion_tokens"));
                    return vo;
                });
    }

    public List<Map<String, Object>> getPersonalTrend(String period, Long userId) {
        String interval = "day".equals(period) ? "'hour'" : ("week".equals(period) ? "'day'" : "'day'");
        String sql = "SELECT DATE_TRUNC(" + interval + ", create_time) as period, COUNT(*) as calls, " +
                "COALESCE(AVG(duration_ms), 0) as avg_duration " +
                "FROM t_rag_trace_run WHERE user_id = ? AND create_time > NOW() - INTERVAL '30 days' " +
                "GROUP BY period ORDER BY period";
        return jdbcTemplate.queryForList(sql, userId);
    }

    public List<Map<String, Object>> getAdminTrend(String period) {
        String interval = "day".equals(period) ? "'hour'" : "'day'";
        String sql = "SELECT DATE_TRUNC(" + interval + ", create_time) as period, COUNT(*) as calls, " +
                "COUNT(DISTINCT user_id) as users " +
                "FROM t_rag_trace_run WHERE create_time > NOW() - INTERVAL '30 days' " +
                "GROUP BY period ORDER BY period";
        return jdbcTemplate.queryForList(sql);
    }

    public List<Map<String, Object>> getUserRanking(int days) {
        return jdbcTemplate.queryForList(
                "SELECT user_id, COUNT(*) as call_count, COALESCE(AVG(duration_ms),0) as avg_duration " +
                "FROM t_rag_trace_run WHERE create_time > NOW() - INTERVAL '" + days + " days' " +
                "GROUP BY user_id ORDER BY call_count DESC LIMIT 10");
    }

    public List<Map<String, Object>> getKbStats() {
        return jdbcTemplate.queryForList(
                "SELECT kb_id, COUNT(*) as doc_count FROM t_knowledge_document WHERE deleted = 0 GROUP BY kb_id");
    }

    public Map<String, Object> getPersonalOverview(Long userId) {
        return jdbcTemplate.queryForMap(
                "SELECT COUNT(*) as total_calls, COALESCE(AVG(duration_ms),0) as avg_duration, " +
                "COALESCE(SUM(CASE WHEN status='SUCCESS' THEN 1 ELSE 0 END) * 100.0 / NULLIF(COUNT(*),0), 0) as success_rate " +
                "FROM t_rag_trace_run WHERE user_id = ?", userId);
    }

    public Map<String, Object> getAdminOverview() {
        return jdbcTemplate.queryForMap(
                "SELECT COUNT(DISTINCT user_id) as total_users, COUNT(*) as total_calls, " +
                "COALESCE(SUM(CASE WHEN status='SUCCESS' THEN 1 ELSE 0 END) * 100.0 / NULLIF(COUNT(*),0), 0) as success_rate " +
                "FROM t_rag_trace_run");
    }

    public Map<String, Object> getTraceDetail(String traceId) {
        Map<String, Object> run = jdbcTemplate.queryForMap(
                "SELECT trace_id, status, duration_ms, create_time FROM t_rag_trace_run WHERE trace_id = ?", traceId);
        List<Map<String, Object>> nodes = jdbcTemplate.queryForList(
                "SELECT node_id, node_name, node_type, status, duration_ms, input_data, output_data, prompt_tokens, completion_tokens, error_message " +
                "FROM t_rag_trace_node WHERE trace_id = ? ORDER BY start_time ASC", traceId);
        run.put("nodes", nodes);
        return run;
    }

    private String buildWhere(String dimension, Long tenantId, Long userId) {
        if ("TENANT".equals(dimension) && tenantId != null) {
            return " AND tenant_id = " + tenantId;
        }
        if ("USER".equals(dimension) && userId != null) {
            return " AND user_id = " + userId;
        }
        return "";
    }
}