package com.example.zuoaiagent.dashboard.service.impl;

import com.example.zuoaiagent.dashboard.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    private static final Logger log = LoggerFactory.getLogger(DashboardServiceImpl.class);

    private final JdbcTemplate jdbcTemplate;

    private static final double INPUT_RATE = 0.0005;
    private static final double OUTPUT_RATE = 0.0015;

    @Override
    public Map<String, Object> getOverview(Long userId) {
        try {
            LocalDate today = LocalDate.now();
            LocalDate monthStart = today.withDayOfMonth(1);

            Map<String, Object> result = new LinkedHashMap<>();

            Map<String, Object> todayToken = queryTokenStats(userId, today, today);
            result.put("todayTokens", todayToken.get("totalTokens"));
            result.put("todayInputTokens", todayToken.get("inputTokens"));
            result.put("todayOutputTokens", todayToken.get("outputTokens"));

            Map<String, Object> monthToken = queryTokenStats(userId, monthStart, today);
            result.put("monthTokens", monthToken.get("totalTokens"));
            result.put("monthInputTokens", monthToken.get("inputTokens"));
            result.put("monthOutputTokens", monthToken.get("outputTokens"));

            Long todayMessages = queryMessageCount(userId, today, today);
            result.put("todayMessages", todayMessages);

            Long monthMessages = queryMessageCount(userId, monthStart, today);
            result.put("monthMessages", monthMessages);

            return result;
        } catch (Exception e) {
            log.error("查询看板总览失败", e);
            return Map.of(
                "todayTokens", 0L, "todayInputTokens", 0L, "todayOutputTokens", 0L,
                "monthTokens", 0L, "monthInputTokens", 0L, "monthOutputTokens", 0L,
                "todayMessages", 0L, "monthMessages", 0L
            );
        }
    }

    @Override
    public Map<String, Object> getTokenTrend(Long userId, int days, LocalDate startDate, LocalDate endDate) {
        try {
            boolean isToday = startDate.equals(endDate) && startDate.equals(LocalDate.now());
            if (isToday) {
                return getTokenTrendByHour(userId, startDate);
            } else {
                return getTokenTrendByDay(userId, startDate, endDate);
            }
        } catch (Exception e) {
            log.error("查询 Token 趋势失败", e);
            return Map.of("days", days, "trendData", List.of());
        }
    }

    private Map<String, Object> getTokenTrendByHour(Long userId, LocalDate date) {
        String userIdFilter = " AND (user_id = ? OR user_id IS NULL) ";

        String sql = "SELECT " +
                    "EXTRACT(HOUR FROM created_at) as hour, " +
                    "COALESCE(SUM(input_tokens), 0) as input_tokens, " +
                    "COALESCE(SUM(output_tokens), 0) as output_tokens, " +
                    "COUNT(*) as usage_count, " +
                    "COALESCE(AVG(input_tokens + output_tokens), 0) as avg_tokens, " +
                    "COALESCE(MAX(input_tokens + output_tokens), 0) as max_tokens, " +
                    "COALESCE(MIN(input_tokens + output_tokens), 0) as min_tokens " +
                    "FROM t_token_usage " +
                    "WHERE DATE(created_at) = ?::date " + userIdFilter +
                    "GROUP BY EXTRACT(HOUR FROM created_at) " +
                    "ORDER BY hour ASC";

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql,
            date.format(DateTimeFormatter.ISO_DATE), userId);

        Map<Integer, long[]> dataMap = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            int hour = ((Number) row.get("hour")).intValue();
            long input = ((Number) row.getOrDefault("input_tokens", 0L)).longValue();
            long output = ((Number) row.getOrDefault("output_tokens", 0L)).longValue();
            long usage = ((Number) row.getOrDefault("usage_count", 0L)).longValue();
            long avg = ((Number) row.getOrDefault("avg_tokens", 0L)).longValue();
            long max = ((Number) row.getOrDefault("max_tokens", 0L)).longValue();
            long min = ((Number) row.getOrDefault("min_tokens", 0L)).longValue();
            dataMap.put(hour, new long[]{input, output, usage, avg, max, min});
        }

        List<Map<String, Object>> trendData = new ArrayList<>();
        for (int h = 0; h < 24; h++) {
            long[] values = dataMap.getOrDefault(h, new long[]{0, 0, 0, 0, 0, 0});
            Map<String, Object> trend = new LinkedHashMap<>();
            trend.put("hour", h);
            trend.put("date", String.format("%02d:00", h));
            trend.put("inputTokens", values[0]);
            trend.put("outputTokens", values[1]);
            trend.put("totalTokens", values[0] + values[1]);
            trend.put("usageCount", values[2]);
            trend.put("avgTokens", values[3]);
            trend.put("maxTokens", values[4]);
            trend.put("minTokens", values[5]);
            trendData.add(trend);
        }

        return Map.of(
            "date", date.toString(),
            "granularity", "hour",
            "trendData", trendData
        );
    }

    private Map<String, Object> getTokenTrendByDay(Long userId, LocalDate startDate, LocalDate endDate) {
        String userIdFilter = " AND (user_id = ? OR user_id IS NULL) ";

        String sql = "SELECT " +
                    "DATE(created_at) as date, " +
                    "COALESCE(SUM(input_tokens), 0) as input_tokens, " +
                    "COALESCE(SUM(output_tokens), 0) as output_tokens, " +
                    "COUNT(*) as usage_count, " +
                    "COALESCE(AVG(input_tokens + output_tokens), 0) as avg_tokens, " +
                    "COALESCE(MAX(input_tokens + output_tokens), 0) as max_tokens, " +
                    "COALESCE(MIN(input_tokens + output_tokens), 0) as min_tokens " +
                    "FROM t_token_usage " +
                    "WHERE DATE(created_at) >= ?::date AND DATE(created_at) <= ?::date " + userIdFilter +
                    "GROUP BY DATE(created_at) " +
                    "ORDER BY date ASC";

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql,
            startDate.format(DateTimeFormatter.ISO_DATE),
            endDate.format(DateTimeFormatter.ISO_DATE), userId);

        Map<String, long[]> dataMap = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String date = row.get("date") != null ? row.get("date").toString() : null;
            if (date != null) {
                long input = ((Number) row.getOrDefault("input_tokens", 0L)).longValue();
                long output = ((Number) row.getOrDefault("output_tokens", 0L)).longValue();
                long usage = ((Number) row.getOrDefault("usage_count", 0L)).longValue();
                long avg = ((Number) row.getOrDefault("avg_tokens", 0L)).longValue();
                long max = ((Number) row.getOrDefault("max_tokens", 0L)).longValue();
                long min = ((Number) row.getOrDefault("min_tokens", 0L)).longValue();
                dataMap.put(date, new long[]{input, output, usage, avg, max, min});
            }
        }

        long totalDays = ChronoUnit.DAYS.between(startDate, endDate) + 1;
        List<Map<String, Object>> trendData = new ArrayList<>();
        for (int i = 0; i < totalDays; i++) {
            LocalDate d = startDate.plusDays(i);
            String dateStr = d.format(DateTimeFormatter.ISO_DATE);
            long[] values = dataMap.getOrDefault(dateStr, new long[]{0, 0, 0, 0, 0, 0});
            Map<String, Object> trend = new LinkedHashMap<>();
            trend.put("date", dateStr);
            trend.put("inputTokens", values[0]);
            trend.put("outputTokens", values[1]);
            trend.put("totalTokens", values[0] + values[1]);
            trend.put("usageCount", values[2]);
            trend.put("avgTokens", values[3]);
            trend.put("maxTokens", values[4]);
            trend.put("minTokens", values[5]);
            trendData.add(trend);
        }

        return Map.of(
            "startDate", startDate.toString(),
            "endDate", endDate.toString(),
            "days", (int) totalDays,
            "granularity", "day",
            "trendData", trendData
        );
    }

    @Override
    public Map<String, Object> getMessageTrend(Long userId, int days, LocalDate startDate, LocalDate endDate) {
        try {
            boolean isToday = startDate.equals(endDate) && startDate.equals(LocalDate.now());
            if (isToday) {
                return getMessageTrendByHour(userId, startDate);
            } else {
                return getMessageTrendByDay(userId, startDate, endDate);
            }
        } catch (Exception e) {
            log.error("查询消息频次趋势失败", e);
            return Map.of("days", days, "trendData", List.of());
        }
    }

    private Map<String, Object> getMessageTrendByHour(Long userId, LocalDate date) {
        String userIdFilter = " AND (c.user_id = ? OR c.user_id IS NULL) ";

        String sql = "SELECT " +
                    "EXTRACT(HOUR FROM m.create_time) as hour, " +
                    "COUNT(*) as message_count " +
                    "FROM t_chat_message_raw m " +
                    "JOIN t_conversation c ON m.conversation_id = c.id " +
                    "WHERE DATE(m.create_time) = ?::date " + userIdFilter +
                    "GROUP BY EXTRACT(HOUR FROM m.create_time) " +
                    "ORDER BY hour ASC";

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql,
            date.format(DateTimeFormatter.ISO_DATE), userId);

        Map<Integer, Long> dataMap = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            int hour = ((Number) row.get("hour")).intValue();
            long count = ((Number) row.getOrDefault("message_count", 0L)).longValue();
            dataMap.put(hour, count);
        }

        List<Map<String, Object>> trendData = new ArrayList<>();
        for (int h = 0; h < 24; h++) {
            long count = dataMap.getOrDefault(h, 0L);
            Map<String, Object> trend = new LinkedHashMap<>();
            trend.put("hour", h);
            trend.put("date", String.format("%02d:00", h));
            trend.put("messageCount", count);
            trendData.add(trend);
        }

        return Map.of(
            "date", date.toString(),
            "granularity", "hour",
            "trendData", trendData
        );
    }

    private Map<String, Object> getMessageTrendByDay(Long userId, LocalDate startDate, LocalDate endDate) {
        String userIdFilter = " AND (c.user_id = ? OR c.user_id IS NULL) ";

        String sql = "SELECT " +
                    "DATE(m.create_time) as date, " +
                    "COUNT(*) as message_count " +
                    "FROM t_chat_message_raw m " +
                    "JOIN t_conversation c ON m.conversation_id = c.id " +
                    "WHERE DATE(m.create_time) >= ?::date AND DATE(m.create_time) <= ?::date " + userIdFilter +
                    "GROUP BY DATE(m.create_time) " +
                    "ORDER BY date ASC";

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql,
            startDate.format(DateTimeFormatter.ISO_DATE),
            endDate.format(DateTimeFormatter.ISO_DATE), userId);

        Map<String, Long> dataMap = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String date = row.get("date") != null ? row.get("date").toString() : null;
            if (date != null) {
                long count = ((Number) row.getOrDefault("message_count", 0L)).longValue();
                dataMap.put(date, count);
            }
        }

        long totalDays = ChronoUnit.DAYS.between(startDate, endDate) + 1;
        List<Map<String, Object>> trendData = new ArrayList<>();
        for (int i = 0; i < totalDays; i++) {
            LocalDate d = startDate.plusDays(i);
            String dateStr = d.format(DateTimeFormatter.ISO_DATE);
            long count = dataMap.getOrDefault(dateStr, 0L);
            Map<String, Object> trend = new LinkedHashMap<>();
            trend.put("date", dateStr);
            trend.put("messageCount", count);
            trendData.add(trend);
        }

        return Map.of(
            "startDate", startDate.toString(),
            "endDate", endDate.toString(),
            "days", (int) totalDays,
            "granularity", "day",
            "trendData", trendData
        );
    }

    @Override
    public Map<String, Object> getRetrievalTrend(Long userId, int days, LocalDate startDate, LocalDate endDate) {
        try {
            boolean isToday = startDate.equals(endDate) && startDate.equals(LocalDate.now());
            if (isToday) {
                return getRetrievalTrendByHour(userId, startDate);
            } else {
                return getRetrievalTrendByDay(userId, startDate, endDate);
            }
        } catch (Exception e) {
            log.error("查询检索耗时趋势失败", e);
            return Map.of("days", days, "trendData", List.of());
        }
    }

    private Map<String, Object> getRetrievalTrendByHour(Long userId, LocalDate date) {
        String userIdFilter = " AND (user_id = ? OR user_id IS NULL) ";

        String sql = "SELECT " +
                    "EXTRACT(HOUR FROM created_at) as hour, " +
                    "COUNT(*) as total_count, " +
                    "SUM(CASE WHEN relevance_score >= 0.7 THEN 1 ELSE 0 END) as hit_count, " +
                    "COALESCE(AVG(latency_ms), 0) as avg_latency, " +
                    "COALESCE(MAX(latency_ms), 0) as max_latency, " +
                    "COALESCE(MIN(latency_ms), 0) as min_latency " +
                    "FROM t_retrieval_log " +
                    "WHERE DATE(created_at) = ?::date " + userIdFilter +
                    "GROUP BY EXTRACT(HOUR FROM created_at) " +
                    "ORDER BY hour ASC";

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql,
            date.format(DateTimeFormatter.ISO_DATE), userId);

        Map<Integer, double[]> dataMap = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            int hour = ((Number) row.get("hour")).intValue();
            long total = ((Number) row.getOrDefault("total_count", 0L)).longValue();
            long hit = ((Number) row.getOrDefault("hit_count", 0L)).longValue();
            double avgLatency = row.get("avg_latency") != null ? ((Number) row.get("avg_latency")).doubleValue() : 0.0;
            double maxLatency = row.get("max_latency") != null ? ((Number) row.get("max_latency")).doubleValue() : 0.0;
            double minLatency = row.get("min_latency") != null ? ((Number) row.get("min_latency")).doubleValue() : 0.0;
            dataMap.put(hour, new double[]{total, hit, avgLatency, maxLatency, minLatency});
        }

        List<Map<String, Object>> trendData = new ArrayList<>();
        for (int h = 0; h < 24; h++) {
            double[] values = dataMap.getOrDefault(h, new double[]{0, 0, 0, 0, 0});
            long total = (long) values[0];
            long hit = (long) values[1];
            double hitRate = total > 0 ? (hit * 100.0) / total : 0.0;
            Map<String, Object> trend = new LinkedHashMap<>();
            trend.put("hour", h);
            trend.put("date", String.format("%02d:00", h));
            trend.put("totalCount", total);
            trend.put("hitCount", hit);
            trend.put("hitRate", Math.round(hitRate * 100.0) / 100.0);
            trend.put("avgLatencyMs", Math.round(values[2] * 100.0) / 100.0);
            trend.put("maxLatencyMs", Math.round(values[3] * 100.0) / 100.0);
            trend.put("minLatencyMs", Math.round(values[4] * 100.0) / 100.0);
            trendData.add(trend);
        }

        return Map.of(
            "date", date.toString(),
            "granularity", "hour",
            "trendData", trendData
        );
    }

    private Map<String, Object> getRetrievalTrendByDay(Long userId, LocalDate startDate, LocalDate endDate) {
        String userIdFilter = " AND (user_id = ? OR user_id IS NULL) ";

        String sql = "SELECT " +
                    "DATE(created_at) as date, " +
                    "COUNT(*) as total_count, " +
                    "SUM(CASE WHEN relevance_score >= 0.7 THEN 1 ELSE 0 END) as hit_count, " +
                    "COALESCE(AVG(latency_ms), 0) as avg_latency, " +
                    "COALESCE(MAX(latency_ms), 0) as max_latency, " +
                    "COALESCE(MIN(latency_ms), 0) as min_latency " +
                    "FROM t_retrieval_log " +
                    "WHERE DATE(created_at) >= ?::date AND DATE(created_at) <= ?::date " + userIdFilter +
                    "GROUP BY DATE(created_at) " +
                    "ORDER BY date ASC";

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql,
            startDate.format(DateTimeFormatter.ISO_DATE),
            endDate.format(DateTimeFormatter.ISO_DATE), userId);

        Map<String, double[]> dataMap = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String date = row.get("date") != null ? row.get("date").toString() : null;
            if (date != null) {
                long total = ((Number) row.getOrDefault("total_count", 0L)).longValue();
                long hit = ((Number) row.getOrDefault("hit_count", 0L)).longValue();
                double avgLatency = row.get("avg_latency") != null ? ((Number) row.get("avg_latency")).doubleValue() : 0.0;
                double maxLatency = row.get("max_latency") != null ? ((Number) row.get("max_latency")).doubleValue() : 0.0;
                double minLatency = row.get("min_latency") != null ? ((Number) row.get("min_latency")).doubleValue() : 0.0;
                dataMap.put(date, new double[]{total, hit, avgLatency, maxLatency, minLatency});
            }
        }

        long totalDays = ChronoUnit.DAYS.between(startDate, endDate) + 1;
        List<Map<String, Object>> trendData = new ArrayList<>();
        for (int i = 0; i < totalDays; i++) {
            LocalDate d = startDate.plusDays(i);
            String dateStr = d.format(DateTimeFormatter.ISO_DATE);
            double[] values = dataMap.getOrDefault(dateStr, new double[]{0, 0, 0, 0, 0});
            long total = (long) values[0];
            long hit = (long) values[1];
            double hitRate = total > 0 ? (hit * 100.0) / total : 0.0;
            Map<String, Object> trend = new LinkedHashMap<>();
            trend.put("date", dateStr);
            trend.put("totalCount", total);
            trend.put("hitCount", hit);
            trend.put("hitRate", Math.round(hitRate * 100.0) / 100.0);
            trend.put("avgLatencyMs", Math.round(values[2] * 100.0) / 100.0);
            trend.put("maxLatencyMs", Math.round(values[3] * 100.0) / 100.0);
            trend.put("minLatencyMs", Math.round(values[4] * 100.0) / 100.0);
            trendData.add(trend);
        }

        return Map.of(
            "startDate", startDate.toString(),
            "endDate", endDate.toString(),
            "days", (int) totalDays,
            "granularity", "day",
            "trendData", trendData
        );
    }

    @Override
    public Map<String, Object> getTopKnowledgeBases(Long userId, int days, LocalDate startDate, LocalDate endDate, int topN) {
        try {
            String userIdFilter = " AND (r.user_id = ? OR r.user_id IS NULL) ";
            String sql = "SELECT " +
                        "kb.id, kb.name, kb.description, " +
                        "COUNT(DISTINCT r.conversation_id) as visit_count, " +
                        "COUNT(DISTINCT r.message_id) as query_count, " +
                        "(SELECT COUNT(*) FROM t_knowledge_document d WHERE d.kb_id = kb.id AND d.deleted = 0) as file_count " +
                        "FROM t_knowledge_base kb " +
                        "LEFT JOIN t_retrieval_log r ON kb.id::varchar = r.knowledge_base_id " +
                        "    AND r.created_at >= ?::timestamp AND r.created_at <= ?::timestamp " +
                        "    " + userIdFilter + " " +
                        "WHERE kb.deleted = 0 " +
                        "GROUP BY kb.id, kb.name, kb.description " +
                        "ORDER BY visit_count DESC " +
                        "LIMIT ?";

            String startTs = startDate.atStartOfDay().toString();
            String endTs = endDate.atTime(23, 59, 59).toString();

            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, startTs, endTs, userId, topN);

            return Map.of(
                "topN", topN,
                "startDate", startDate.toString(),
                "endDate", endDate.toString(),
                "knowledgeBases", rows
            );
        } catch (Exception e) {
            log.error("查询 Top 知识库失败", e);
            return Map.of("topN", topN, "knowledgeBases", List.of());
        }
    }

    private Map<String, Object> queryTokenStats(Long userId, LocalDate start, LocalDate end) {
        String userIdFilter = " AND (user_id = ? OR user_id IS NULL) ";

        String sql = "SELECT " +
                    "COALESCE(SUM(input_tokens), 0) as input_tokens, " +
                    "COALESCE(SUM(output_tokens), 0) as output_tokens, " +
                    "COUNT(*) as usage_count " +
                    "FROM t_token_usage " +
                    "WHERE DATE(created_at) >= ?::date AND DATE(created_at) <= ?::date " + userIdFilter;

        List<Map<String, Object>> result = jdbcTemplate.queryForList(sql,
            start.format(DateTimeFormatter.ISO_DATE),
            end.format(DateTimeFormatter.ISO_DATE), userId);

        Map<String, Object> row = result.isEmpty() ? Map.of() : result.get(0);

        long inputTokens = ((Number) row.getOrDefault("input_tokens", 0L)).longValue();
        long outputTokens = ((Number) row.getOrDefault("output_tokens", 0L)).longValue();

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("inputTokens", inputTokens);
        stats.put("outputTokens", outputTokens);
        stats.put("totalTokens", inputTokens + outputTokens);
        stats.put("usageCount", row.getOrDefault("usage_count", 0L));

        return stats;
    }

    private Long queryMessageCount(Long userId, LocalDate start, LocalDate end) {
        try {
            String userIdFilter = " AND (c.user_id = ? OR c.user_id IS NULL) ";

            String sql = "SELECT COUNT(*) FROM t_chat_message_raw m " +
                        "JOIN t_conversation c ON m.conversation_id = c.id " +
                        "WHERE DATE(m.create_time) >= ?::date AND DATE(m.create_time) <= ?::date " + userIdFilter;

            Long count = jdbcTemplate.queryForObject(sql, Long.class,
                start.format(DateTimeFormatter.ISO_DATE),
                end.format(DateTimeFormatter.ISO_DATE), userId);

            return count != null ? count : 0L;
        } catch (Exception e) {
            log.error("查询消息数失败", e);
            return 0L;
        }
    }
}
