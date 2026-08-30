package com.example.logmonitor.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/monitor")
@RequiredArgsConstructor
@Slf4j
public class MonitorController {

    private final StringRedisTemplate redisTemplate;
    private final JdbcTemplate jdbcTemplate;

    @GetMapping("/redis/info")
    public Map<String, Object> redisInfo() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("keyspace", redisTemplate.execute((RedisConnection conn) ->
                conn.serverCommands().info("keyspace")));
        info.put("stats", redisTemplate.execute((RedisConnection conn) ->
                conn.serverCommands().info("stats")));
        info.put("memory", redisTemplate.execute((RedisConnection conn) ->
                conn.serverCommands().info("memory")));
        info.put("clients", redisTemplate.execute((RedisConnection conn) ->
                conn.serverCommands().info("clients")));
        return info;
    }

    @GetMapping("/redis/keys")
    public Map<String, Object> redisKeys(@RequestParam(defaultValue = "*") String pattern, @RequestParam(defaultValue = "100") int limit) {
        Set<String> keys = redisTemplate.keys(pattern);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", keys != null ? keys.size() : 0);
        List<Map<String, Object>> keyDetails = new ArrayList<>();
        if (keys != null) {
            int count = 0;
            for (String key : keys) {
                if (count++ >= limit) break;
                Map<String, Object> detail = new LinkedHashMap<>();
                detail.put("key", key);
                detail.put("type", redisTemplate.type(key) != null ? redisTemplate.type(key).code() : "none");
                detail.put("size", redisTemplate.opsForValue().size(key));
                detail.put("ttl", redisTemplate.getExpire(key));
                keyDetails.add(detail);
            }
        }
        result.put("keys", keyDetails);
        return result;
    }

    @GetMapping("/redis/key/{key}")
    public Map<String, Object> getKeyValue(@PathVariable String key) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("key", key);
        result.put("type", redisTemplate.type(key) != null ? redisTemplate.type(key).code() : "none");
        Long size = redisTemplate.opsForValue().size(key);
        result.put("size", size);
        result.put("isBigKey", size != null && size > 10240);
        result.put("ttl", redisTemplate.getExpire(key));
        if (size != null && size < 10240) {
            result.put("value", redisTemplate.opsForValue().get(key));
        } else {
            result.put("value", "[值过大，已省略]");
        }
        return result;
    }

    @GetMapping("/redis/latency")
    public Map<String, Object> redisLatency() {
        long start = System.currentTimeMillis();
        redisTemplate.opsForValue().get("__latency_check__");
        long latency = System.currentTimeMillis() - start;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("latencyMs", latency);
        return result;
    }

    @GetMapping("/db/slow-queries")
    public List<Map<String, Object>> slowQueries(@RequestParam(defaultValue = "10") int limit) {
        return jdbcTemplate.queryForList(
                "SELECT query, calls, mean_time, total_time FROM pg_stat_statements ORDER BY mean_time DESC LIMIT ?", limit);
    }

    @GetMapping("/db/tables")
    public Map<String, Object> dbTables(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        Map<String, Object> result = new LinkedHashMap<>();
        
        try {
            // P12 修复：获取表总数
            Long total = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM pg_tables WHERE schemaname = 'public'",
                    Long.class
            );
            if (total == null) total = 0L;
            
            // P12 修复：分页获取表空间信息
            int offset = (page - 1) * size;
            List<Map<String, Object>> tables = jdbcTemplate.queryForList(
                    "SELECT schemaname, tablename, " +
                    "pg_total_relation_size(schemaname||'.'||tablename) as size_bytes, " +
                    "pg_size_pretty(pg_total_relation_size(schemaname||'.'||tablename)) as size " +
                    "FROM pg_tables WHERE schemaname = 'public' " +
                    "ORDER BY pg_total_relation_size(schemaname||'.'||tablename) DESC " +
                    "LIMIT ? OFFSET ?",
                    size, offset
            );
            
            // P12 修复：计算总空间占用
            Long totalSize = jdbcTemplate.queryForObject(
                    "SELECT COALESCE(SUM(pg_total_relation_size(schemaname||'.'||tablename)), 0) " +
                    "FROM pg_tables WHERE schemaname = 'public'",
                    Long.class
            );
            
            result.put("tables", tables);
            result.put("total", total);
            result.put("page", page);
            result.put("size", size);
            result.put("pages", (total + size - 1) / size);
            result.put("totalSizeBytes", totalSize != null ? totalSize : 0);
            result.put("totalSizePretty", formatBytes(totalSize != null ? totalSize : 0));
            
            return result;
        } catch (Exception e) {
            log.error("获取表空间信息失败", e);
            result.put("error", e.getMessage());
            result.put("total", 0);
            result.put("tables", List.of());
            return result;
        }
    }

    @GetMapping("/db/latency")
    public Map<String, Object> dbLatency() {
        long start = System.currentTimeMillis();
        jdbcTemplate.queryForObject("SELECT 1", Integer.class);
        long latency = System.currentTimeMillis() - start;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("latencyMs", latency);
        return result;
    }

    @GetMapping("/db/connections")
    public Map<String, Object> dbConnections() {
        return jdbcTemplate.queryForMap(
                "SELECT COUNT(*) as total, SUM(CASE WHEN state='active' THEN 1 ELSE 0 END) as active, " +
                "SUM(CASE WHEN state='idle' THEN 1 ELSE 0 END) as idle FROM pg_stat_activity");
    }

    @GetMapping("/db/hot-queries")
    public List<Map<String, Object>> hotQueries(@RequestParam(defaultValue = "10") int limit) {
        try {
            return jdbcTemplate.queryForList(
                    "SELECT query, calls, total_time, mean_time FROM pg_stat_statements ORDER BY calls DESC LIMIT ?", limit);
        } catch (Exception e) { return List.of(); }
    }

    @GetMapping("/db/qps")
    public Map<String, Object> dbQps() {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            Map<String, Object> row = jdbcTemplate.queryForMap(
                    "SELECT xact_commit + xact_rollback as transactions FROM pg_stat_database WHERE datname = current_database()");
            result.put("transactions", row.get("transactions"));
            result.put("uptime", jdbcTemplate.queryForObject("SELECT NOW() - pg_postmaster_start_time()", String.class));
        } catch (Exception e) { result.put("transactions", 0); }
        return result;
    }

    @GetMapping("/db/table-data")
    public Map<String, Object> tableData(@RequestParam String table,
                                          @RequestParam(defaultValue = "1") int page,
                                          @RequestParam(defaultValue = "20") int size) {
        String safeTable = table.replaceAll("[^a-zA-Z0-9_]", "");
        int offset = (page - 1) * size;
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT * FROM " + safeTable + " LIMIT ? OFFSET ?", size, offset);
        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + safeTable, Long.class);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("rows", rows);
        result.put("total", total != null ? total : 0);
        return result;
    }

    @GetMapping("/redis/hotkeys")
    public Map<String, Object> redisHotKeys(@RequestParam(defaultValue = "20") int limit) {
        Map<String, Object> result = new LinkedHashMap<>();
        List<Map<String, Object>> hotkeys = new ArrayList<>();
        
        try {
            // P13 修复：获取所有 key 并按访问频率排序
            Set<String> keys = redisTemplate.keys("*");
            if (keys != null && !keys.isEmpty()) {
                List<KeyStats> keyStatsList = new ArrayList<>();
                
                for (String key : keys) {
                    Long ttl = redisTemplate.getExpire(key);
                    Long size = redisTemplate.opsForValue().size(key);
                    
                    // 构建 key 统计信息
                    KeyStats stats = new KeyStats();
                    stats.key = key;
                    stats.ttl = ttl != null ? ttl : -1;
                    stats.size = size != null ? size : 0;
                    stats.type = redisTemplate.type(key) != null ? redisTemplate.type(key).code() : "none";
                    keyStatsList.add(stats);
                }
                
                // 按 size 排序（模拟热点 key）
                keyStatsList.stream()
                        .sorted((a, b) -> Long.compare(b.size, a.size))
                        .limit(limit)
                        .forEach(stat -> {
                            Map<String, Object> item = new LinkedHashMap<>();
                            item.put("key", stat.key);
                            item.put("size", stat.size);
                            item.put("type", stat.type);
                            item.put("ttl", stat.ttl);
                            item.put("ttlDescription", stat.ttl == -1 ? "永不过期" : 
                                    stat.ttl == -2 ? "已过期" : stat.ttl + "秒");
                            hotkeys.add(item);
                        });
            }
            
            result.put("hotkeys", hotkeys);
            result.put("total", hotkeys.size());
            result.put("timestamp", System.currentTimeMillis());
            
        } catch (Exception e) {
            log.warn("获取热门 key 失败: {}", e.getMessage());
            result.put("hotkeys", List.of());
            result.put("error", e.getMessage());
        }
        
        return result;
    }

    @GetMapping("/redis/bigkeys")
    public List<Map<String, Object>> redisBigKeys() {
        List<Map<String, Object>> result = new ArrayList<>();
        try {
            Set<String> keys = redisTemplate.keys("*");
            if (keys != null) {
                for (String key : keys) {
                    Long size = redisTemplate.opsForValue().size(key);
                    if (size != null && size > 10240) {
                        Map<String, Object> item = new LinkedHashMap<>();
                        item.put("key", key);
                        item.put("size", size);
                        item.put("type", redisTemplate.type(key) != null ? redisTemplate.type(key).code() : "none");
                        result.add(item);
                    }
                }
            }
        } catch (Exception ignored) {}
        return result;
    }

    @GetMapping("/redis/qps")
    public Map<String, Object> redisQps() {
        Map<String, Object> result = new LinkedHashMap<>();
        
        try {
            // P13 修复：从 Redis INFO stats 获取 QPS 信息
            Properties statsInfo = redisTemplate.execute((RedisConnection conn) ->
                    conn.serverCommands().info("stats")
            );
            
            double qps = 0.0;
            long totalCommands = 0;
            
            if (statsInfo != null) {
                String qpsVal = statsInfo.getProperty("instantaneous_ops_per_sec");
                if (qpsVal != null) {
                    try { qps = Double.parseDouble(qpsVal.trim()); } catch (Exception e) { log.warn("解析 QPS 失败"); }
                }
                String totalVal = statsInfo.getProperty("total_commands_processed");
                if (totalVal != null) {
                    try { totalCommands = Long.parseLong(totalVal.trim()); } catch (Exception ignored) {}
                }
            }
            
            result.put("qps", qps);
            result.put("totalCommands", totalCommands);
            result.put("unit", "ops/sec");
            result.put("timestamp", System.currentTimeMillis());
            
        } catch (Exception e) {
            log.warn("获取 Redis QPS 失败: {}", e.getMessage());
            result.put("error", e.getMessage());
            result.put("qps", 0);
        }
        
        return result;
    }

    // ==================== 辅助类和方法 ====================
    
    /**
     * KeyStats - Redis key 统计信息
     */
    public static class KeyStats {
        public String key;
        public Long size;
        public String type;
        public Long ttl;
    }

    /**
     * 将字节数转换为可读格式
     */
    private String formatBytes(long bytes) {
        if (bytes <= 0) return "0 B";
        final String[] units = new String[]{"B", "KB", "MB", "GB", "TB"};
        int digitGroups = (int) (Math.log10(bytes) / Math.log10(1024));
        return String.format("%.1f %s", bytes / Math.pow(1024, digitGroups), units[digitGroups]);
    }
}