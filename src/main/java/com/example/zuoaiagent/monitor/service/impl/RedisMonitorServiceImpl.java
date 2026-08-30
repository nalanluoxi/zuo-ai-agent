package com.example.zuoaiagent.monitor.service.impl;

import com.example.zuoaiagent.monitor.service.RedisMonitorService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * P25：Redis 监控服务实现
 */
@Service
@RequiredArgsConstructor
public class RedisMonitorServiceImpl implements RedisMonitorService {

    private static final Logger log = LoggerFactory.getLogger(RedisMonitorServiceImpl.class);
    private final JdbcTemplate jdbcTemplate;
    private final StringRedisTemplate redisTemplate;

    private static final long BIGKEY_THRESHOLD = 1024 * 1024; // 1MB

    @Override
    public Map<String, Object> getQpsTrend(int days) {
        try {
            String sql = "SELECT " +
                        "created_at::date as date, " +
                        "COALESCE(SUM(qps), 0) as qps, " +
                        "COALESCE(AVG(qps), 0) as avg_qps " +
                        "FROM t_redis_qps " +
                        "WHERE created_at >= CURRENT_DATE - ? * INTERVAL '1 day' " +
                        "GROUP BY created_at::date " +
                        "ORDER BY date ASC";
            
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, days);
            
            List<Map<String, Object>> trendData = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                Map<String, Object> trend = new LinkedHashMap<>();
                trend.put("date", row.get("date"));
                trend.put("qps", ((Number) row.getOrDefault("qps", 0L)).longValue());
                trend.put("avgQps", ((Number) row.getOrDefault("avg_qps", 0.0)).doubleValue());
                trendData.add(trend);
            }
            
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("days", days);
            result.put("trendData", trendData);
            result.put("count", trendData.size());
            
            return result;
        } catch (Exception e) {
            log.error("查询 Redis QPS 趋势失败：", e);
            return Map.of("days", days, "trendData", List.of(), "count", 0);
        }
    }

    @Override
    public Map<String, Object> getMemoryTrend(int days) {
        try {
            String sql = "SELECT " +
                        "created_at::date as date, " +
                        "COALESCE(MAX(used_memory), 0) as used_memory, " +
                        "COALESCE(MAX(max_memory), 0) as max_memory, " +
                        "COALESCE(AVG(used_memory), 0) as avg_used " +
                        "FROM t_redis_memory " +
                        "WHERE created_at >= CURRENT_DATE - ? * INTERVAL '1 day' " +
                        "GROUP BY created_at::date " +
                        "ORDER BY date ASC";
            
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, days);
            
            List<Map<String, Object>> trendData = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                long usedMemory = ((Number) row.getOrDefault("used_memory", 0L)).longValue();
                long maxMemory = ((Number) row.getOrDefault("max_memory", 0L)).longValue();
                double memoryRate = maxMemory > 0 ? (usedMemory * 100.0) / maxMemory : 0.0;
                
                Map<String, Object> trend = new LinkedHashMap<>();
                trend.put("date", row.get("date"));
                trend.put("usedMemory", formatBytes(usedMemory));
                trend.put("usedMemoryBytes", usedMemory);
                trend.put("maxMemory", formatBytes(maxMemory));
                trend.put("maxMemoryBytes", maxMemory);
                trend.put("memoryRate", String.format("%.2f%%", memoryRate));
                trendData.add(trend);
            }
            
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("days", days);
            result.put("trendData", trendData);
            result.put("count", trendData.size());
            
            return result;
        } catch (Exception e) {
            log.error("查询 Redis 内存趋势失败：", e);
            return Map.of("days", days, "trendData", List.of(), "count", 0);
        }
    }

    @Override
    public Map<String, Object> getBuckets(int page, int size) {
        try {
            // 按 Key 前缀分桶统计
            String sql = "SELECT " +
                        "SPLIT_PART(key_name, ':', 1) as bucket, " +
                        "COUNT(*) as key_count, " +
                        "COALESCE(SUM(key_size), 0) as total_size " +
                        "FROM t_redis_key_stats " +
                        "GROUP BY SPLIT_PART(key_name, ':', 1) " +
                        "ORDER BY key_count DESC " +
                        "LIMIT ? OFFSET ?";
            
            List<Map<String, Object>> content = jdbcTemplate.queryForList(
                sql, 
                size, 
                (page - 1) * size
            );
            
            // 获取总数
            String countSql = "SELECT COUNT(DISTINCT SPLIT_PART(key_name, ':', 1)) as total " +
                             "FROM t_redis_key_stats";
            Integer total = jdbcTemplate.queryForObject(countSql, Integer.class);
            if (total == null) total = 0;
            
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("content", content);
            result.put("totalElements", total);
            result.put("totalPages", (total + size - 1) / size);
            result.put("currentPage", page);
            result.put("pageSize", size);
            
            return result;
        } catch (Exception e) {
            log.error("查询 Redis Key 分桶统计失败：", e);
            return Map.of(
                "content", List.of(),
                "totalElements", 0,
                "totalPages", 0,
                "currentPage", page,
                "pageSize", size
            );
        }
    }

    @Override
    public Map<String, Object> getHotKeys() {
        try {
            String sql = "SELECT " +
                        "key_name, " +
                        "access_count, " +
                        "last_access_time, " +
                        "key_size " +
                        "FROM t_redis_key_stats " +
                        "ORDER BY access_count DESC " +
                        "LIMIT 20";
            
            List<Map<String, Object>> hotKeys = jdbcTemplate.queryForList(sql);
            
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("hotKeys", hotKeys);
            result.put("count", hotKeys.size());
            
            return result;
        } catch (Exception e) {
            log.error("查询 Redis 热 Key 失败：", e);
            return Map.of("hotKeys", List.of(), "count", 0);
        }
    }

    @Override
    public Map<String, Object> getKeyDetail(String key) {
        try {
            String sql = "SELECT " +
                        "key_name, key_type, key_size, " +
                        "access_count, last_access_time, " +
                        "ttl, expire_time, " +
                        "created_at, updated_at " +
                        "FROM t_redis_key_stats " +
                        "WHERE key_name = ?";
            
            List<Map<String, Object>> results = jdbcTemplate.queryForList(sql, key);
            
            if (results.isEmpty()) {
                return Map.of("found", false, "message", "Key 不存在");
            }
            
            Map<String, Object> detail = new LinkedHashMap<>(results.get(0));
            detail.put("found", true);
            detail.put("keySizeFormatted", formatBytes(((Number) detail.getOrDefault("key_size", 0L)).longValue()));
            
            return detail;
        } catch (Exception e) {
            log.error("查询 Redis Key 详情失败：", e);
            return Map.of("found", false, "error", e.getMessage());
        }
    }

    @Override
    public Map<String, Object> getBigKeys(int page, int size) {
        try {
            String sql = "SELECT " +
                        "key_name, key_type, key_size, " +
                        "access_count, created_at " +
                        "FROM t_redis_key_stats " +
                        "WHERE key_size > ? " +
                        "ORDER BY key_size DESC " +
                        "LIMIT ? OFFSET ?";
            
            List<Map<String, Object>> content = jdbcTemplate.queryForList(
                sql,
                BIGKEY_THRESHOLD,
                size,
                (page - 1) * size
            );
            
            // 格式化大小
            for (Map<String, Object> item : content) {
                long bytes = ((Number) item.getOrDefault("key_size", 0L)).longValue();
                item.put("keySizeFormatted", formatBytes(bytes));
            }
            
            // 获取总数
            String countSql = "SELECT COUNT(*) as total FROM t_redis_key_stats WHERE key_size > ?";
            Integer total = jdbcTemplate.queryForObject(countSql, Integer.class, BIGKEY_THRESHOLD);
            if (total == null) total = 0;
            
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("content", content);
            result.put("totalElements", total);
            result.put("totalPages", (total + size - 1) / size);
            result.put("currentPage", page);
            result.put("pageSize", size);
            result.put("threshold", formatBytes(BIGKEY_THRESHOLD));
            
            return result;
        } catch (Exception e) {
            log.error("查询 Redis BigKey 失败：", e);
            return Map.of(
                "content", List.of(),
                "totalElements", 0,
                "totalPages", 0,
                "currentPage", page,
                "pageSize", size
            );
        }
    }

    @Override
    public Map<String, Object> getRedisInfo() {
        try {
            // 直接从 Redis 获取实时信息
            Properties info = redisTemplate.getConnectionFactory().getConnection().info();
            
            Map<String, Object> result = new LinkedHashMap<>();
            
            // 基础信息
            result.put("redisVersion", info.getProperty("redis_version", "unknown"));
            result.put("uptimeInSeconds", Long.parseLong(info.getProperty("uptime_in_seconds", "0")));
            
            // 连接信息
            result.put("connectedClients", Long.parseLong(info.getProperty("connected_clients", "0")));
            
            // 内存信息
            long usedMemory = Long.parseLong(info.getProperty("used_memory", "0"));
            long maxMemory = Long.parseLong(info.getProperty("maxmemory", "0"));
            result.put("usedMemory", usedMemory);
            result.put("usedMemoryFormatted", formatBytes(usedMemory));
            result.put("maxMemory", maxMemory);
            result.put("maxMemoryFormatted", formatBytes(maxMemory));
            if (maxMemory > 0) {
                double memoryRate = (usedMemory * 100.0) / maxMemory;
                result.put("memoryRate", String.format("%.2f%%", memoryRate));
            }
            
            // Key 统计
            long totalKeys = 0;
            for (String key : info.stringPropertyNames()) {
                if (key.startsWith("db") && key.contains("keys=")) {
                    String value = info.getProperty(key);
                    if (value != null && value.contains(",")) {
                        String keysPart = value.split(",")[0];
                        if (keysPart.contains("=")) {
                            totalKeys += Long.parseLong(keysPart.split("=")[1]);
                        }
                    }
                }
            }
            result.put("totalKeys", totalKeys);
            
            // 命中率
            long keyspaceHits = Long.parseLong(info.getProperty("keyspace_hits", "0"));
            long keyspaceMisses = Long.parseLong(info.getProperty("keyspace_misses", "0"));
            long totalCommands = keyspaceHits + keyspaceMisses;
            double hitRate = totalCommands > 0 ? (keyspaceHits * 100.0) / totalCommands : 0.0;
            result.put("keyspaceHits", keyspaceHits);
            result.put("keyspaceMisses", keyspaceMisses);
            result.put("hitRate", String.format("%.2f%%", hitRate));
            
            // QPS
            result.put("instantaneousOpsPerSecond", Long.parseLong(info.getProperty("instantaneous_ops_per_sec", "0")));
            
            result.put("available", true);
            
            return result;
        } catch (Exception e) {
            log.error("查询 Redis 信息失败：", e);
            return Map.of("available", false, "error", e.getMessage());
        }
    }

    /**
     * 格式化字节大小
     */
    private String formatBytes(long bytes) {
        if (bytes <= 0) return "0 B";
        final String[] units = new String[]{"B", "KB", "MB", "GB", "TB"};
        int digitGroups = (int) (Math.log10(bytes) / Math.log10(1024));
        return String.format("%.2f %s", bytes / Math.pow(1024, digitGroups), units[digitGroups]);
    }
}
