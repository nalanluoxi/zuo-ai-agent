package com.example.zuoaiagent.dashboard.service.impl;

import com.example.zuoaiagent.dashboard.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.example.zuoaiagent.config.TenantContextHolder;
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
    public Map<String, Object> getTokenTrend(Long userId, int days, LocalDate startDate, LocalDate endDate, String usageType) {
        try {
            boolean isToday = startDate.equals(endDate) && startDate.equals(LocalDate.now());
            if (isToday) {
                return getTokenTrendByHour(userId, startDate, usageType);
            } else {
                return getTokenTrendByDay(userId, startDate, endDate, usageType);
            }
        } catch (Exception e) {
            log.error("查询 Token 趋势失败", e);
            return Map.of("days", days, "trendData", List.of());
        }
    }

    private Map<String, Object> getTokenTrendByHour(Long userId, LocalDate date, String usageType) {
        String userIdFilter = " AND user_id = ? ";
        String usageTypeFilter = usageType != null ? " AND usage_type = ? " : "";
        Object[] baseParams = usageType != null
            ? new Object[]{date.format(DateTimeFormatter.ISO_DATE), userId, usageType}
            : new Object[]{date.format(DateTimeFormatter.ISO_DATE), userId};

        String sql = "SELECT " +
                    "EXTRACT(HOUR FROM created_at) as hour, " +
                    "COALESCE(SUM(input_tokens), 0) as input_tokens, " +
                    "COALESCE(SUM(output_tokens), 0) as output_tokens, " +
                    "COUNT(*) as usage_count, " +
                    "COALESCE(AVG(input_tokens + output_tokens), 0) as avg_tokens, " +
                    "COALESCE(MAX(input_tokens + output_tokens), 0) as max_tokens, " +
                    "COALESCE(MIN(input_tokens + output_tokens), 0) as min_tokens " +
                    "FROM t_token_usage " +
                    "WHERE DATE(created_at) = ?::date " + userIdFilter + usageTypeFilter +
                    "GROUP BY EXTRACT(HOUR FROM created_at) " +
                    "ORDER BY hour ASC";

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, baseParams);

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

    private Map<String, Object> getTokenTrendByDay(Long userId, LocalDate startDate, LocalDate endDate, String usageType) {
        String userIdFilter = " AND user_id = ? ";
        String usageTypeFilter = usageType != null ? " AND usage_type = ? " : "";
        Object[] baseParams = usageType != null
            ? new Object[]{startDate.format(DateTimeFormatter.ISO_DATE), endDate.format(DateTimeFormatter.ISO_DATE), userId, usageType}
            : new Object[]{startDate.format(DateTimeFormatter.ISO_DATE), endDate.format(DateTimeFormatter.ISO_DATE), userId};

        String sql = "SELECT " +
                    "DATE(created_at) as date, " +
                    "COALESCE(SUM(input_tokens), 0) as input_tokens, " +
                    "COALESCE(SUM(output_tokens), 0) as output_tokens, " +
                    "COUNT(*) as usage_count, " +
                    "COALESCE(AVG(input_tokens + output_tokens), 0) as avg_tokens, " +
                    "COALESCE(MAX(input_tokens + output_tokens), 0) as max_tokens, " +
                    "COALESCE(MIN(input_tokens + output_tokens), 0) as min_tokens " +
                    "FROM t_token_usage " +
                    "WHERE DATE(created_at) >= ?::date AND DATE(created_at) <= ?::date " + userIdFilter + usageTypeFilter +
                    "GROUP BY DATE(created_at) " +
                    "ORDER BY date ASC";

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, baseParams);

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
        String userIdFilter = " AND c.user_id = ? ";

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
        String userIdFilter = " AND c.user_id = ? ";

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
        String userIdFilter = " AND user_id = ? ";

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
        String userIdFilter = " AND user_id = ? ";

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
            // userId 为空 = 全局（全局看板）；非空 = 个人视角
            // 知识库可见性过滤：个人视角只显示 自己的 + 全局 PUBLIC + 本租户 TEAM，修复私密知识库名称泄露
            String userIdFilter = userId != null ? " AND r.user_id = ? " : "";
            String visibilityFilter = userId != null
                    ? " AND (kb.owner_id = ? OR kb.visibility = 'PUBLIC' OR (kb.visibility = 'TEAM' AND kb.tenant_id = ?)) "
                    : "";
            String sql = "SELECT " +
                        "kb.id, kb.name, kb.description, " +
                        "COUNT(DISTINCT r.conversation_id) as visit_count, " +
                        "COUNT(DISTINCT r.message_id) as query_count, " +
                        "(SELECT COUNT(*) FROM t_knowledge_document d WHERE d.kb_id = kb.id AND d.deleted = 0) as file_count " +
                        "FROM t_knowledge_base kb " +
                        "LEFT JOIN t_retrieval_log r ON kb.id::varchar = r.knowledge_base_id " +
                        "    AND r.created_at >= ?::timestamp AND r.created_at <= ?::timestamp " +
                        "    " + userIdFilter + " " +
                        "WHERE kb.deleted = 0 " + visibilityFilter +
                        "GROUP BY kb.id, kb.name, kb.description " +
                        "ORDER BY visit_count DESC " +
                        "LIMIT ?";

            String startTs = startDate.atStartOfDay().toString();
            String endTs = endDate.atTime(23, 59, 59).toString();

            List<Object> params = new ArrayList<>();
            params.add(startTs);
            params.add(endTs);
            if (userId != null) {
                params.add(userId);          // r.user_id
                params.add(userId);          // kb.owner_id
                params.add(TenantContextHolder.getTenantId()); // kb.tenant_id（TEAM 级别）
            }
            params.add(topN);
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, params.toArray());

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
        String userIdFilter = " AND user_id = ? ";

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
            String userIdFilter = " AND c.user_id = ? ";

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

    @Override
    public Map<String, Object> getIngestionOverview(Long userId, Long kbId) {
        try {
            // 查询知识库总文件数、成功分块文件数、向量入库文件数、分块总数、失败分块文件数、入库失败数
            String sql = "SELECT " +
                        "COUNT(DISTINCT doc_id) FILTER (WHERE stage = 'upload') as total_files, " +
                        "COUNT(DISTINCT doc_id) FILTER (WHERE stage = 'chunk' AND status = 'success') as success_chunk_files, " +
                        "COUNT(DISTINCT doc_id) FILTER (WHERE stage = 'vectorize' AND status = 'success') as vectorize_files, " +
                        "COALESCE(SUM(chunks_count) FILTER (WHERE stage = 'chunk'), 0) as total_chunks, " +
                        "COUNT(DISTINCT doc_id) FILTER (WHERE stage = 'chunk' AND status = 'failed') as failed_chunk_files, " +
                        "COUNT(DISTINCT doc_id) FILTER (WHERE stage = 'complete' AND status = 'failed') as ingestion_failed_files " +
                        "FROM t_ingestion_log " +
                        "WHERE kb_id IN (SELECT id FROM t_knowledge_base WHERE deleted = 0"
                        + (userId != null ? " AND owner_id = ?" : "") + ")";

            List<Object> params = new ArrayList<>();
            // userId 为空 = 全局（全局看板）
            if (userId != null) {
                params.add(userId);
            }

            if (kbId != null) {
                sql += " AND kb_id = ?";
                params.add(kbId);
            }

            Map<String, Object> row = jdbcTemplate.queryForMap(sql, params.toArray());

            long totalFiles = ((Number) row.getOrDefault("total_files", 0L)).longValue();
            long successChunkFiles = ((Number) row.getOrDefault("success_chunk_files", 0L)).longValue();
            long vectorizeFiles = ((Number) row.getOrDefault("vectorize_files", 0L)).longValue();
            long totalChunks = ((Number) row.getOrDefault("total_chunks", 0L)).longValue();
            long failedChunkFiles = ((Number) row.getOrDefault("failed_chunk_files", 0L)).longValue();
            long ingestionFailedFiles = ((Number) row.getOrDefault("ingestion_failed_files", 0L)).longValue();

            return Map.of(
                "totalFiles", totalFiles,
                "successChunkFiles", successChunkFiles,
                "vectorizeFiles", vectorizeFiles,
                "totalChunks", totalChunks,
                "failedChunkFiles", failedChunkFiles,
                "ingestionFailedFiles", ingestionFailedFiles
            );
        } catch (Exception e) {
            log.error("查询入库概览失败", e);
            return Map.of(
                "totalFiles", 0L,
                "successChunkFiles", 0L,
                "vectorizeFiles", 0L,
                "totalChunks", 0L,
                "failedChunkFiles", 0L,
                "ingestionFailedFiles", 0L
            );
        }
    }

    @Override
    public Map<String, Object> getIngestionDurationStats(Long userId, Long kbId, int days, LocalDate startDate, LocalDate endDate) {
        try {
            // 按阶段统计耗时（upload、parse、chunk、vectorize）
            String sql = "SELECT " +
                        "stage, " +
                        "COALESCE(AVG(duration_ms), 0) as avg_duration, " +
                        "COALESCE(MAX(duration_ms), 0) as max_duration, " +
                        "COALESCE(MIN(duration_ms), 0) as min_duration " +
                        "FROM t_ingestion_log " +
                        "WHERE kb_id IN (SELECT id FROM t_knowledge_base WHERE deleted = 0"
                        + (userId != null ? " AND owner_id = ?" : "") + ") " +
                        "AND DATE(created_at) >= ?::date AND DATE(created_at) <= ?::date";

            List<Object> params = new ArrayList<>();
            if (userId != null) {
                params.add(userId);
            }
            params.add(startDate.format(DateTimeFormatter.ISO_DATE));
            params.add(endDate.format(DateTimeFormatter.ISO_DATE));

            if (kbId != null) {
                sql += " AND kb_id = ?";
                params.add(kbId);
            }

            sql += " GROUP BY stage";

            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, params.toArray());

            Map<String, Object> result = new LinkedHashMap<>();
            for (Map<String, Object> row : rows) {
                String stage = (String) row.get("stage");
                double avgDuration = ((Number) row.getOrDefault("avg_duration", 0.0)).doubleValue();
                double maxDuration = ((Number) row.getOrDefault("max_duration", 0.0)).doubleValue();
                double minDuration = ((Number) row.getOrDefault("min_duration", 0.0)).doubleValue();

                result.put(stage, Map.of(
                    "avg", Math.round(avgDuration * 100.0) / 100.0,
                    "max", Math.round(maxDuration * 100.0) / 100.0,
                    "min", Math.round(minDuration * 100.0) / 100.0
                ));
            }

            return result;
        } catch (Exception e) {
            log.error("查询入库耗时统计失败", e);
            return Map.of();
        }
    }

    @Override
    public Map<String, Object> getIngestionTrend(Long userId, int days, LocalDate startDate, LocalDate endDate) {
        try {
            boolean isToday = startDate.equals(endDate) && startDate.equals(LocalDate.now());
            if (isToday) {
                return getIngestionTrendByHour(userId, startDate);
            } else {
                return getIngestionTrendByDay(userId, startDate, endDate);
            }
        } catch (Exception e) {
            log.error("查询入库趋势失败", e);
            return Map.of("days", days, "trendData", List.of());
        }
    }

    private Map<String, Object> getIngestionTrendByHour(Long userId, LocalDate date) {
        String sql = "SELECT " +
                    "EXTRACT(HOUR FROM created_at) as hour, " +
                    "COUNT(DISTINCT CASE WHEN stage = 'complete' AND status = 'success' THEN doc_id END) as success_docs, " +
                    "COUNT(DISTINCT CASE WHEN status = 'failed' THEN doc_id END) as failed_docs, " +
                    "SUM(CASE WHEN stage = 'chunk' THEN chunks_count ELSE 0 END) as chunks_count, " +
                    "COALESCE(AVG(CASE WHEN stage = 'complete' THEN total_duration_ms END), 0) as avg_duration_ms " +
                    "FROM t_ingestion_log " +
                    "WHERE DATE(created_at) = ?::date " +
                    "AND kb_id IN (SELECT id FROM t_knowledge_base WHERE deleted = 0"
                    + (userId != null ? " AND owner_id = ?" : "") + ") " +
                    "GROUP BY EXTRACT(HOUR FROM created_at) " +
                    "ORDER BY hour ASC";

        List<Map<String, Object>> rows = userId != null
                ? jdbcTemplate.queryForList(sql, date.format(DateTimeFormatter.ISO_DATE), userId)
                : jdbcTemplate.queryForList(sql, date.format(DateTimeFormatter.ISO_DATE));

        Map<Integer, long[]> dataMap = new LinkedHashMap<>();
        Map<Integer, Double> durationMap = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            int hour = ((Number) row.get("hour")).intValue();
            long successDocs = ((Number) row.getOrDefault("success_docs", 0L)).longValue();
            long failedDocs = ((Number) row.getOrDefault("failed_docs", 0L)).longValue();
            long chunksCount = ((Number) row.getOrDefault("chunks_count", 0L)).longValue();
            double avgDuration = ((Number) row.getOrDefault("avg_duration_ms", 0.0)).doubleValue();
            dataMap.put(hour, new long[]{successDocs, failedDocs, chunksCount});
            durationMap.put(hour, avgDuration);
        }

        List<Map<String, Object>> trendData = new ArrayList<>();
        for (int h = 0; h < 24; h++) {
            long[] values = dataMap.getOrDefault(h, new long[]{0, 0, 0});
            double avgDuration = durationMap.getOrDefault(h, 0.0);
            Map<String, Object> trend = new LinkedHashMap<>();
            trend.put("hour", h);
            trend.put("date", String.format("%02d:00", h));
            trend.put("successDocs", values[0]);
            trend.put("failedDocs", values[1]);
            trend.put("chunksCount", values[2]);
            trend.put("avgDurationMs", Math.round(avgDuration * 100.0) / 100.0);
            trendData.add(trend);
        }

        return Map.of(
            "date", date.toString(),
            "granularity", "hour",
            "trendData", trendData
        );
    }

    private Map<String, Object> getIngestionTrendByDay(Long userId, LocalDate startDate, LocalDate endDate) {
        String sql = "SELECT " +
                    "DATE(created_at) as date, " +
                    "COUNT(DISTINCT CASE WHEN stage = 'complete' AND status = 'success' THEN doc_id END) as success_docs, " +
                    "COUNT(DISTINCT CASE WHEN status = 'failed' THEN doc_id END) as failed_docs, " +
                    "SUM(CASE WHEN stage = 'chunk' THEN chunks_count ELSE 0 END) as chunks_count, " +
                    "COALESCE(AVG(CASE WHEN stage = 'complete' THEN total_duration_ms END), 0) as avg_duration_ms " +
                    "FROM t_ingestion_log " +
                    "WHERE DATE(created_at) >= ?::date AND DATE(created_at) <= ?::date " +
                    "AND kb_id IN (SELECT id FROM t_knowledge_base WHERE deleted = 0"
                    + (userId != null ? " AND owner_id = ?" : "") + ") " +
                    "GROUP BY DATE(created_at) " +
                    "ORDER BY date ASC";

        List<Map<String, Object>> rows = userId != null
                ? jdbcTemplate.queryForList(sql,
                    startDate.format(DateTimeFormatter.ISO_DATE),
                    endDate.format(DateTimeFormatter.ISO_DATE),
                    userId)
                : jdbcTemplate.queryForList(sql,
                    startDate.format(DateTimeFormatter.ISO_DATE),
                    endDate.format(DateTimeFormatter.ISO_DATE));

        Map<String, long[]> dataMap = new LinkedHashMap<>();
        Map<String, Double> durationMap = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String date = row.get("date") != null ? row.get("date").toString() : null;
            if (date != null) {
                long successDocs = ((Number) row.getOrDefault("success_docs", 0L)).longValue();
                long failedDocs = ((Number) row.getOrDefault("failed_docs", 0L)).longValue();
                long chunksCount = ((Number) row.getOrDefault("chunks_count", 0L)).longValue();
                double avgDuration = ((Number) row.getOrDefault("avg_duration_ms", 0.0)).doubleValue();
                dataMap.put(date, new long[]{successDocs, failedDocs, chunksCount});
                durationMap.put(date, avgDuration);
            }
        }

        long totalDays = ChronoUnit.DAYS.between(startDate, endDate) + 1;
        List<Map<String, Object>> trendData = new ArrayList<>();
        for (int i = 0; i < totalDays; i++) {
            LocalDate d = startDate.plusDays(i);
            String dateStr = d.format(DateTimeFormatter.ISO_DATE);
            long[] values = dataMap.getOrDefault(dateStr, new long[]{0, 0, 0});
            double avgDuration = durationMap.getOrDefault(dateStr, 0.0);
            Map<String, Object> trend = new LinkedHashMap<>();
            trend.put("date", dateStr);
            trend.put("successDocs", values[0]);
            trend.put("failedDocs", values[1]);
            trend.put("chunksCount", values[2]);
            trend.put("avgDurationMs", Math.round(avgDuration * 100.0) / 100.0);
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
    public Map<String, Object> getRagStageTrend(Long userId, String stage, int days, LocalDate startDate, LocalDate endDate) {
        try {
            boolean isToday = startDate.equals(endDate) && startDate.equals(LocalDate.now());
            if (isToday) {
                return getRagStageTrendByHour(userId, stage, startDate);
            } else {
                return getRagStageTrendByDay(userId, stage, startDate, endDate);
            }
        } catch (Exception e) {
            log.error("查询 RAG 阶段耗时趋势失败, stage={}", stage, e);
            return Map.of("days", days, "trendData", List.of());
        }
    }

    private Map<String, Object> getRagStageTrendByHour(Long userId, String stage, LocalDate date) {
        String sql = "SELECT " +
                    "EXTRACT(HOUR FROM n.start_time) as hour, " +
                    "COALESCE(AVG(n.duration_ms), 0) as avg_duration, " +
                    "COALESCE(MAX(n.duration_ms), 0) as max_duration, " +
                    "COALESCE(MIN(n.duration_ms), 0) as min_duration " +
                    "FROM t_rag_trace_node n " +
                    "JOIN t_rag_trace_run r ON n.trace_id = r.trace_id " +
                    "LEFT JOIN t_conversation c ON r.conversation_id = c.id " +
                    "WHERE n.node_type = ? AND n.duration_ms IS NOT NULL " +
                    "AND DATE(n.start_time) = ?::date " +
                    "AND c.user_id = ? " +
                    "GROUP BY EXTRACT(HOUR FROM n.start_time) " +
                    "ORDER BY hour ASC";

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql,
            stage, date.format(DateTimeFormatter.ISO_DATE), userId);

        Map<Integer, double[]> dataMap = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            int hour = ((Number) row.get("hour")).intValue();
            double avg = ((Number) row.getOrDefault("avg_duration", 0.0)).doubleValue();
            double max = ((Number) row.getOrDefault("max_duration", 0.0)).doubleValue();
            double min = ((Number) row.getOrDefault("min_duration", 0.0)).doubleValue();
            dataMap.put(hour, new double[]{avg, max, min});
        }

        List<Map<String, Object>> trendData = new ArrayList<>();
        for (int h = 0; h < 24; h++) {
            double[] values = dataMap.getOrDefault(h, new double[]{0, 0, 0});
            Map<String, Object> trend = new LinkedHashMap<>();
            trend.put("hour", h);
            trend.put("date", String.format("%02d:00", h));
            trend.put("avgDurationMs", Math.round(values[0] * 100.0) / 100.0);
            trend.put("maxDurationMs", Math.round(values[1] * 100.0) / 100.0);
            trend.put("minDurationMs", Math.round(values[2] * 100.0) / 100.0);
            trendData.add(trend);
        }

        return Map.of(
            "date", date.toString(),
            "stage", stage,
            "granularity", "hour",
            "trendData", trendData
        );
    }

    private Map<String, Object> getRagStageTrendByDay(Long userId, String stage, LocalDate startDate, LocalDate endDate) {
        String sql = "SELECT " +
                    "DATE(n.start_time) as date, " +
                    "COALESCE(AVG(n.duration_ms), 0) as avg_duration, " +
                    "COALESCE(MAX(n.duration_ms), 0) as max_duration, " +
                    "COALESCE(MIN(n.duration_ms), 0) as min_duration " +
                    "FROM t_rag_trace_node n " +
                    "JOIN t_rag_trace_run r ON n.trace_id = r.trace_id " +
                    "LEFT JOIN t_conversation c ON r.conversation_id = c.id " +
                    "WHERE n.node_type = ? AND n.duration_ms IS NOT NULL " +
                    "AND DATE(n.start_time) >= ?::date AND DATE(n.start_time) <= ?::date " +
                    "AND c.user_id = ? " +
                    "GROUP BY DATE(n.start_time) " +
                    "ORDER BY date ASC";

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql,
            stage,
            startDate.format(DateTimeFormatter.ISO_DATE),
            endDate.format(DateTimeFormatter.ISO_DATE),
            userId);

        Map<String, double[]> dataMap = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String date = row.get("date") != null ? row.get("date").toString() : null;
            if (date != null) {
                double avg = ((Number) row.getOrDefault("avg_duration", 0.0)).doubleValue();
                double max = ((Number) row.getOrDefault("max_duration", 0.0)).doubleValue();
                double min = ((Number) row.getOrDefault("min_duration", 0.0)).doubleValue();
                dataMap.put(date, new double[]{avg, max, min});
            }
        }

        long totalDays = ChronoUnit.DAYS.between(startDate, endDate) + 1;
        List<Map<String, Object>> trendData = new ArrayList<>();
        for (int i = 0; i < totalDays; i++) {
            LocalDate d = startDate.plusDays(i);
            String dateStr = d.format(DateTimeFormatter.ISO_DATE);
            double[] values = dataMap.getOrDefault(dateStr, new double[]{0, 0, 0});
            Map<String, Object> trend = new LinkedHashMap<>();
            trend.put("date", dateStr);
            trend.put("avgDurationMs", Math.round(values[0] * 100.0) / 100.0);
            trend.put("maxDurationMs", Math.round(values[1] * 100.0) / 100.0);
            trend.put("minDurationMs", Math.round(values[2] * 100.0) / 100.0);
            trendData.add(trend);
        }

        return Map.of(
            "startDate", startDate.toString(),
            "endDate", endDate.toString(),
            "days", (int) totalDays,
            "stage", stage,
            "granularity", "day",
            "trendData", trendData
        );
    }
}
