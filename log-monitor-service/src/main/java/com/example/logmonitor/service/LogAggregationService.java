package com.example.logmonitor.service;
import com.example.logmonitor.entity.LogTokenDO;
import com.example.logmonitor.mapper.LogTokenMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class LogAggregationService {

    private final LogTokenMapper logTokenMapper;
    private final JdbcTemplate jdbcTemplate;

    private static final Pattern WORD_PATTERN = Pattern.compile("[a-zA-Z_][a-zA-Z0-9_]{2,}");
    private static final Set<String> STOP_WORDS = Set.of(
            "the", "and", "for", "not", "with", "from", "that", "this", "have", "has",
            "was", "were", "been", "are", "but", "all", "can", "had", "get", "got",
            "its", "may", "will", "one", "our", "out", "you", "see", "way"
    );

    /** 对日志消息做分词并写入倒排索引（按小时分桶） */
    public void indexLogMessage(String message, String serviceName, String logLevel, LocalDateTime logTime) {
        if (message == null || message.isEmpty()) return;

        Set<String> tokens = tokenize(message);
        LocalDateTime bucket = logTime.withMinute(0).withSecond(0).withNano(0);

        for (String token : tokens) {
            LogTokenDO entry = new LogTokenDO();
            entry.setToken(token);
            entry.setServiceName(serviceName);
            entry.setLogLevel(logLevel);
            entry.setTimeBucket(bucket);
            entry.setDocCount(1L);
            entry.setCreateTime(LocalDateTime.now());

            // 幂等：同 token+service+level+bucket 合并计数
            String existSql = "SELECT id, doc_count FROM t_log_token " +
                             "WHERE token = ? AND service_name = ? AND log_level = ? AND time_bucket = ?";
            List<Map<String, Object>> existing = jdbcTemplate.queryForList(
                    existSql, token, serviceName, logLevel, bucket);

            if (existing.isEmpty()) {
                logTokenMapper.insert(entry);
            } else {
                Long existingId = ((Number) existing.get(0).get("id")).longValue();
                Long existingCount = ((Number) existing.get(0).get("doc_count")).longValue();
                String updateSql = "UPDATE t_log_token SET doc_count = ? WHERE id = ?";
                jdbcTemplate.update(updateSql, existingCount + 1, existingId);
            }
        }
    }

    /** 搜索：输入关键词 → 返回匹配 token + 文档计数 */
    public Map<String, Object> search(String keyword, String serviceName,
                                       LocalDateTime startTime, LocalDateTime endTime,
                                       String granularity) {
        StringBuilder sql = new StringBuilder(
                "SELECT token, service_name, log_level, time_bucket, SUM(doc_count) as total_docs " +
                "FROM t_log_token WHERE 1=1"
        );
        List<Object> params = new ArrayList<>();

        if (keyword != null && !keyword.isEmpty()) {
            sql.append(" AND token ILIKE ?");
            params.add("%" + keyword + "%");
        }
        if (serviceName != null) {
            sql.append(" AND service_name = ?");
            params.add(serviceName);
        }
        if (startTime != null) {
            sql.append(" AND time_bucket >= ?");
            params.add(startTime);
        }
        if (endTime != null) {
            sql.append(" AND time_bucket <= ?");
            params.add(endTime);
        }

        // 按粒度分组
        String bucketExpr = "time_bucket";
        if ("day".equalsIgnoreCase(granularity)) {
            bucketExpr = "date_trunc('day', time_bucket)";
        } else if ("week".equalsIgnoreCase(granularity)) {
            bucketExpr = "date_trunc('week', time_bucket)";
        } else if ("month".equalsIgnoreCase(granularity)) {
            bucketExpr = "date_trunc('month', time_bucket)";
        }

        sql.append(" GROUP BY token, service_name, log_level, ").append(bucketExpr)
           .append(" ORDER BY total_docs DESC LIMIT 200");

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql.toString(), params.toArray());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", rows);
        result.put("count", rows.size());
        return result;
    }

    /** 获取 token 计数趋势（按时间桶） */
    public List<Map<String, Object>> getTokenTrend(String token, String serviceName, int hours) {
        String sql = "SELECT date_trunc('hour', time_bucket) as hour_bucket, " +
                    "SUM(doc_count) as total_count " +
                    "FROM t_log_token " +
                    "WHERE token = ? AND service_name = ? AND time_bucket >= ? " +
                    "GROUP BY hour_bucket ORDER BY hour_bucket ASC";
        return jdbcTemplate.queryForList(sql, token, serviceName, LocalDateTime.now().minusHours(hours));
    }

    /** 简单分词：提取单词，去停用词，转小写 */
    static Set<String> tokenize(String message) {
        Set<String> tokens = new LinkedHashSet<>();
        Matcher matcher = WORD_PATTERN.matcher(message.toLowerCase());
        while (matcher.find()) {
            String word = matcher.group();
            if (!STOP_WORDS.contains(word) && word.length() >= 3) {
                tokens.add(word);
            }
        }
        return tokens;
    }
}
