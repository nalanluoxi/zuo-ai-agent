package com.example.zuoaiagent.dashboard.service.impl;

import com.example.zuoaiagent.dashboard.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * P24：个人看板服务实现
 */
@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    private static final Logger log = LoggerFactory.getLogger(DashboardServiceImpl.class);
    private final JdbcTemplate jdbcTemplate;

    private static final double INPUT_RATE = 0.0005;      // 每千个输入 Token 的成本
    private static final double OUTPUT_RATE = 0.0015;     // 每千个输出 Token 的成本
    private static final double RELEVANCE_THRESHOLD = 0.7; // 命中率阈值

    @Override
    public Map<String, Object> getTokenStats(LocalDate date) {
        String dateStr = date.format(DateTimeFormatter.ISO_DATE);
        
        try {
            // 查询当日 Token 使用情况
            String sql = "SELECT " +
                        "COALESCE(SUM(input_tokens), 0) as input_tokens, " +
                        "COALESCE(SUM(output_tokens), 0) as output_tokens, " +
                        "COUNT(*) as usage_count " +
                        "FROM t_token_usage " +
                        "WHERE DATE(created_at) = ?";
            
            List<Map<String, Object>> result = jdbcTemplate.queryForList(sql, dateStr);
            Map<String, Object> row = result.isEmpty() ? Map.of() : result.get(0);
            
            long inputTokens = ((Number) row.getOrDefault("input_tokens", 0L)).longValue();
            long outputTokens = ((Number) row.getOrDefault("output_tokens", 0L)).longValue();
            long usageCount = ((Number) row.getOrDefault("usage_count", 0L)).longValue();
            
            // 计算成本
            double inputCost = (inputTokens / 1000.0) * INPUT_RATE;
            double outputCost = (outputTokens / 1000.0) * OUTPUT_RATE;
            double totalCost = inputCost + outputCost;
            
            Map<String, Object> stats = new LinkedHashMap<>();
            stats.put("date", dateStr);
            stats.put("inputTokens", inputTokens);
            stats.put("outputTokens", outputTokens);
            stats.put("totalTokens", inputTokens + outputTokens);
            stats.put("usageCount", usageCount);
            stats.put("inputCost", String.format("%.4f", inputCost));
            stats.put("outputCost", String.format("%.4f", outputCost));
            stats.put("totalCost", String.format("%.4f", totalCost));
            
            return stats;
        } catch (Exception e) {
            log.error("查询 Token 统计失败：", e);
            return Map.of(
                "date", dateStr,
                "inputTokens", 0L,
                "outputTokens", 0L,
                "totalTokens", 0L,
                "usageCount", 0L,
                "totalCost", "0.0000"
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
                long input = ((Number) row.getOrDefault("input_tokens", 0L)).longValue();
                long output = ((Number) row.getOrDefault("output_tokens", 0L)).longValue();
                
                Map<String, Object> trend = new LinkedHashMap<>();
                trend.put("date", row.get("date"));
                trend.put("inputTokens", input);
                trend.put("outputTokens", output);
                trend.put("totalTokens", input + output);
                trendData.add(trend);
            }
            
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("days", days);
            result.put("trendData", trendData);
            result.put("count", trendData.size());
            
            return result;
        } catch (Exception e) {
            log.error("查询 Token 趋势失败：", e);
            return Map.of(
                "days", days,
                "trendData", List.of(),
                "count", 0
            );
        }
    }

    @Override
    public Map<String, Object> getRetrievalStats(LocalDate date) {
        String dateStr = date.format(DateTimeFormatter.ISO_DATE);
        
        try {
            // 查询当日检索情况
            String sql = "SELECT " +
                        "COUNT(*) as total_count, " +
                        "SUM(CASE WHEN relevance_score >= ? THEN 1 ELSE 0 END) as hit_count " +
                        "FROM t_retrieval_log " +
                        "WHERE DATE(created_at) = ?";
            
            List<Map<String, Object>> result = jdbcTemplate.queryForList(sql, RELEVANCE_THRESHOLD, dateStr);
            Map<String, Object> row = result.isEmpty() ? Map.of() : result.get(0);
            
            long totalCount = ((Number) row.getOrDefault("total_count", 0L)).longValue();
            long hitCount = ((Number) row.getOrDefault("hit_count", 0L)).longValue();
            
            double hitRate = totalCount > 0 ? (hitCount * 100.0) / totalCount : 0.0;
            
            Map<String, Object> stats = new LinkedHashMap<>();
            stats.put("date", dateStr);
            stats.put("totalCount", totalCount);
            stats.put("hitCount", hitCount);
            stats.put("hitRate", String.format("%.2f%%", hitRate));
            stats.put("hitRateValue", hitRate);
            
            return stats;
        } catch (Exception e) {
            log.error("查询检索统计失败：", e);
            return Map.of(
                "date", dateStr,
                "totalCount", 0L,
                "hitCount", 0L,
                "hitRate", "0.00%",
                "hitRateValue", 0.0
            );
        }
    }

    @Override
    public Map<String, Object> getRetrievalTrend(int days) {
        try {
            String sql = "SELECT " +
                        "DATE(created_at) as date, " +
                        "COUNT(*) as total_count, " +
                        "SUM(CASE WHEN relevance_score >= ? THEN 1 ELSE 0 END) as hit_count " +
                        "FROM t_retrieval_log " +
                        "WHERE created_at >= CURRENT_DATE - (? || ' days')::INTERVAL " +
                        "GROUP BY DATE(created_at) " +
                        "ORDER BY date ASC";
            
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, RELEVANCE_THRESHOLD, days);
            
            List<Map<String, Object>> trendData = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                long total = ((Number) row.getOrDefault("total_count", 0L)).longValue();
                long hit = ((Number) row.getOrDefault("hit_count", 0L)).longValue();
                double rate = total > 0 ? (hit * 100.0) / total : 0.0;
                
                Map<String, Object> trend = new LinkedHashMap<>();
                trend.put("date", row.get("date"));
                trend.put("totalCount", total);
                trend.put("hitCount", hit);
                trend.put("hitRate", String.format("%.2f", rate));
                trendData.add(trend);
            }
            
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("days", days);
            result.put("trendData", trendData);
            result.put("count", trendData.size());
            
            return result;
        } catch (Exception e) {
            log.error("查询检索趋势失败：", e);
            return Map.of(
                "days", days,
                "trendData", List.of(),
                "count", 0
            );
        }
    }

    @Override
    public Map<String, Object> getTopKnowledgeBases(int limit) {
        try {
            String sql = "SELECT " +
                        "kb.id, kb.name, kb.description, " +
                        "COUNT(DISTINCT c.id) as visit_count, " +
                        "COUNT(DISTINCT f.id) as file_count " +
                        "FROM t_knowledge_base kb " +
                        "LEFT JOIN t_retrieval_log r ON kb.id = r.knowledge_base_id " +
                        "LEFT JOIN t_conversation c ON r.conversation_id = c.id " +
                        "LEFT JOIN t_knowledge_file f ON kb.id = f.knowledge_base_id " +
                        "WHERE kb.deleted = 0 " +
                        "GROUP BY kb.id, kb.name, kb.description " +
                        "ORDER BY visit_count DESC " +
                        "LIMIT ?";
            
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, limit);
            
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("limit", limit);
            result.put("topKnowledgeBases", rows);
            result.put("count", rows.size());
            
            return result;
        } catch (Exception e) {
            log.error("查询 TOP 知识库失败：", e);
            return Map.of(
                "limit", limit,
                "topKnowledgeBases", List.of(),
                "count", 0
            );
        }
    }

    @Override
    public Map<String, Object> getRecentConversations(int limit) {
        try {
            String sql = "SELECT " +
                        "id, title, user_id, " +
                        "(SELECT COUNT(*) FROM t_chat_message_raw WHERE conversation_id = c.id) as message_count, " +
                        "created_at, updated_at " +
                        "FROM t_conversation c " +
                        "ORDER BY updated_at DESC " +
                        "LIMIT ?";
            
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, limit);
            
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("limit", limit);
            result.put("recentConversations", rows);
            result.put("count", rows.size());
            
            return result;
        } catch (Exception e) {
            log.error("查询最近对话失败：", e);
            return Map.of(
                "limit", limit,
                "recentConversations", List.of(),
                "count", 0
            );
        }
    }

    @Override
    public Map<String, Object> getDashboardOverview() {
        try {
            Map<String, Object> overview = new LinkedHashMap<>();
            
            // 添加当日 Token 统计
            overview.put("tokenStats", getTokenStats(LocalDate.now()));
            
            // 添加当日检索命中率
            overview.put("retrievalStats", getRetrievalStats(LocalDate.now()));
            
            // 添加 TOP 5 知识库
            Map<String, Object> topKbs = getTopKnowledgeBases(5);
            overview.put("topKnowledgeBases", topKbs.get("topKnowledgeBases"));
            
            // 添加最近 10 条对话
            Map<String, Object> recentConv = getRecentConversations(10);
            overview.put("recentConversations", recentConv.get("recentConversations"));
            
            // 添加更新时间
            overview.put("updatedAt", new Date());
            
            return overview;
        } catch (Exception e) {
            log.error("查询仪表板总览失败：", e);
            return Map.of(
                "tokenStats", Map.of(),
                "retrievalStats", Map.of(),
                "topKnowledgeBases", List.of(),
                "recentConversations", List.of()
            );
        }
    }
}
