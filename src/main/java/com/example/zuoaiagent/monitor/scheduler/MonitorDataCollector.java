package com.example.zuoaiagent.monitor.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Properties;

/**
 * 监控数据采集器 - 定时采集 DB 和 Redis 监控数据
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MonitorDataCollector {

    private final JdbcTemplate jdbcTemplate;
    private final StringRedisTemplate redisTemplate;

    /**
     * 每分钟采集 DB QPS（事务数）
     */
    @Scheduled(fixedRate = 60000)
    public void collectDbQps() {
        try {
            // 从 pg_stat_database 获取当前事务数
            String sql = "SELECT xact_commit + xact_rollback as total_transactions FROM pg_stat_database WHERE datname = current_database()";
            Long totalTransactions = jdbcTemplate.queryForObject(sql, Long.class);
            
            if (totalTransactions != null) {
                String insertSql = "INSERT INTO t_db_qps (query_count) VALUES (?)";
                jdbcTemplate.update(insertSql, totalTransactions);
                log.debug("DB QPS 采集成功: {}", totalTransactions);
            }
        } catch (Exception e) {
            log.warn("DB QPS 采集失败: {}", e.getMessage());
        }
    }

    /**
     * 每分钟采集 DB 访问流量（读/写）
     */
    @Scheduled(fixedRate = 60000)
    public void collectDbAccess() {
        try {
            // 从 pg_stat_database 获取 tup_returned（读）和 tup_fetched（写）
            String sql = "SELECT tup_returned as read_count, tup_fetched as write_count FROM pg_stat_database WHERE datname = current_database()";
            Map<String, Object> row = jdbcTemplate.queryForMap(sql);
            
            if (row != null) {
                long readCount = ((Number) row.getOrDefault("read_count", 0L)).longValue();
                long writeCount = ((Number) row.getOrDefault("write_count", 0L)).longValue();
                
                String insertSql = "INSERT INTO t_db_access (read_count, write_count) VALUES (?, ?)";
                jdbcTemplate.update(insertSql, readCount, writeCount);
                log.debug("DB 访问流量采集成功: read={}, write={}", readCount, writeCount);
            }
        } catch (Exception e) {
            log.warn("DB 访问流量采集失败: {}", e.getMessage());
        }
    }

    /**
     * 每小时采集表空间
     */
    @Scheduled(fixedRate = 3600000)
    public void collectTablespace() {
        try {
            String sql = "SELECT " +
                        "relname as table_name, " +
                        "pg_total_relation_size(relid) as table_size, " +
                        "pg_indexes_size(relid) as index_size " +
                        "FROM pg_catalog.pg_statio_user_tables " +
                        "ORDER BY pg_total_relation_size(relid) DESC";
            
            var rows = jdbcTemplate.queryForList(sql);
            for (Map<String, Object> row : rows) {
                String tableName = (String) row.get("table_name");
                long tableSize = ((Number) row.getOrDefault("table_size", 0L)).longValue();
                long indexSize = ((Number) row.getOrDefault("index_size", 0L)).longValue();
                
                String insertSql = "INSERT INTO t_tablespace (table_name, table_size, index_size) VALUES (?, ?, ?)";
                jdbcTemplate.update(insertSql, tableName, tableSize, indexSize);
            }
            log.debug("表空间采集成功，共 {} 张表", rows.size());
        } catch (Exception e) {
            log.warn("表空间采集失败: {}", e.getMessage());
        }
    }

    /**
     * 每分钟采集 Redis QPS
     */
    @Scheduled(fixedRate = 60000)
    public void collectRedisQps() {
        try {
            Properties info = redisTemplate.getConnectionFactory().getConnection().info();
            String qpsStr = info.getProperty("instantaneous_ops_per_sec");
            long qps = Long.parseLong(qpsStr != null ? qpsStr : "0");
            
            String insertSql = "INSERT INTO t_redis_qps (qps) VALUES (?)";
            jdbcTemplate.update(insertSql, qps);
            log.debug("Redis QPS 采集成功: {}", qps);
        } catch (Exception e) {
            log.warn("Redis QPS 采集失败: {}", e.getMessage());
        }
    }

    /**
     * 每分钟采集 Redis 内存
     */
    @Scheduled(fixedRate = 60000)
    public void collectRedisMemory() {        try {
            Properties info = redisTemplate.getConnectionFactory().getConnection().info();
            String usedMemoryStr = info.getProperty("used_memory");
            String maxMemoryStr = info.getProperty("maxmemory");
            
            long usedMemory = Long.parseLong(usedMemoryStr != null ? usedMemoryStr : "0");
            long maxMemory = Long.parseLong(maxMemoryStr != null ? maxMemoryStr : "0");
            
            String insertSql = "INSERT INTO t_redis_memory (used_memory, max_memory) VALUES (?, ?)";
            jdbcTemplate.update(insertSql, usedMemory, maxMemory);
            log.debug("Redis 内存采集成功: used={}, max={}", usedMemory, maxMemory);
        } catch (Exception e) {
            log.warn("Redis 内存采集失败: {}", e.getMessage());
        }
    }

    /**
     * 每分钟采集 Redis 延迟（探测 __latency_check__ 往返耗时）
     */
    @Scheduled(fixedRate = 60000)
    public void collectRedisLatency() {
        try {
            long start = System.currentTimeMillis();
            redisTemplate.opsForValue().get("__latency_check__");
            long latencyMs = System.currentTimeMillis() - start;

            String insertSql = "INSERT INTO t_redis_latency (latency_ms) VALUES (?)";
            jdbcTemplate.update(insertSql, latencyMs);
            log.debug("Redis 延迟采集成功: {}ms", latencyMs);
        } catch (Exception e) {
            log.warn("Redis 延迟采集失败: {}", e.getMessage());
        }
    }

    /**
     * 每 5 分钟采集 Redis Key 统计
     */
    @Scheduled(fixedRate = 300000)
    public void collectRedisKeyStats() {
        try {
            // 注意：KEYS * 在生产环境可能阻塞，建议用 SCAN 替代
            var keys = redisTemplate.keys("*");
            if (keys == null || keys.isEmpty()) {
                log.debug("Redis Key 统计采集：无 Key");
                return;
            }
            
            String insertSql = "INSERT INTO t_redis_key_stats (key_name, key_type, key_size, access_count, last_access_time, ttl, expire_time) VALUES (?, ?, ?, ?, ?, ?, ?)";
            
            for (String key : keys) {
                try {
                    String type = redisTemplate.type(key) != null ? redisTemplate.type(key).code() : "unknown";
                    Long size = 0L;
                    if ("string".equals(type)) {
                        size = redisTemplate.opsForValue().size(key) != null ? redisTemplate.opsForValue().size(key) : 0L;
                    }
                    Long ttl = redisTemplate.getExpire(key);
                    
                    jdbcTemplate.update(insertSql, key, type, size, 0, null, ttl, null);
                } catch (Exception e) {
                    log.warn("Redis Key [{}] 统计失败: {}", key, e.getMessage());
                }
            }
            log.debug("Redis Key 统计采集成功，共 {} 个 Key", keys.size());
        } catch (Exception e) {
            log.warn("Redis Key 统计采集失败: {}", e.getMessage());
        }
    }
}
