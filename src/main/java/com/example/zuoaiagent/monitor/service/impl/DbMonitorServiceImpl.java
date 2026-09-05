package com.example.zuoaiagent.monitor.service.impl;

import com.example.zuoaiagent.monitor.service.DbMonitorService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * P26：数据库监控服务实现
 */
@Service
@RequiredArgsConstructor
public class DbMonitorServiceImpl implements DbMonitorService {

    private static final Logger log = LoggerFactory.getLogger(DbMonitorServiceImpl.class);
    private final JdbcTemplate jdbcTemplate;
    private final com.example.zuoaiagent.monitor.support.TrendRangeSupport trendRangeSupport;

    @Override
    public Map<String, Object> getQpsTrend(String period, String startDate, String endDate) {
        var range = trendRangeSupport.resolve(period, startDate, endDate);
        try {
            // query_count 是 pg_stat_database 的累计值，按桶取增量（MAX-MIN）才是该时段事务数
            String sql = "SELECT to_char(created_at, ?) as bucket, " +
                        "COALESCE(MAX(query_count) - MIN(query_count), 0) as transactions, " +
                        "COALESCE(MAX(query_count), 0) as total_count " +
                        "FROM t_db_qps " +
                        "WHERE created_at >= ? AND created_at < ? " +
                        "GROUP BY bucket ORDER BY bucket ASC";

            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql,
                    range.bucketFormat(), range.startTime(), range.endTime());

            Map<String, Map<String, Object>> byBucket = new LinkedHashMap<>();
            for (Map<String, Object> row : rows) {
                Map<String, Object> trend = new LinkedHashMap<>();
                trend.put("qps", ((Number) row.getOrDefault("transactions", 0L)).longValue());
                trend.put("totalCount", ((Number) row.getOrDefault("total_count", 0L)).longValue());
                byBucket.put(String.valueOf(row.get("bucket")), trend);
            }

            List<Map<String, Object>> trendData = trendRangeSupport.fillBuckets(
                    range.buckets(), byBucket, () -> Map.of("qps", 0, "totalCount", 0));

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("period", range.period());
            result.put("trendData", trendData);
            result.put("count", trendData.size());
            return result;
        } catch (Exception e) {
            log.error("查询数据库 QPS 趋势失败：", e);
            return Map.of("period", range.period(), "trendData", List.of(), "count", 0);
        }
    }

    @Override
    public Map<String, Object> getAccessTrend(String period, String startDate, String endDate) {
        var range = trendRangeSupport.resolve(period, startDate, endDate);
        try {
            // read_count/write_count 是累计值，按桶取增量（MAX-MIN）
            String sql = "SELECT to_char(created_at, ?) as bucket, " +
                        "COALESCE(MAX(read_count) - MIN(read_count), 0) as read_count, " +
                        "COALESCE(MAX(write_count) - MIN(write_count), 0) as write_count " +
                        "FROM t_db_access " +
                        "WHERE created_at >= ? AND created_at < ? " +
                        "GROUP BY bucket ORDER BY bucket ASC";

            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql,
                    range.bucketFormat(), range.startTime(), range.endTime());

            Map<String, Map<String, Object>> byBucket = new LinkedHashMap<>();
            for (Map<String, Object> row : rows) {
                long read = ((Number) row.getOrDefault("read_count", 0L)).longValue();
                long write = ((Number) row.getOrDefault("write_count", 0L)).longValue();

                Map<String, Object> trend = new LinkedHashMap<>();
                trend.put("readCount", read);
                trend.put("writeCount", write);
                trend.put("totalCount", read + write);
                byBucket.put(String.valueOf(row.get("bucket")), trend);
            }

            List<Map<String, Object>> trendData = trendRangeSupport.fillBuckets(
                    range.buckets(), byBucket, () -> Map.of("readCount", 0, "writeCount", 0, "totalCount", 0));

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("period", range.period());
            result.put("trendData", trendData);
            result.put("count", trendData.size());
            return result;
        } catch (Exception e) {
            log.error("查询数据库访问趋势失败：", e);
            return Map.of("period", range.period(), "trendData", List.of(), "count", 0);
        }
    }

    @Override
    public Map<String, Object> getTablespaceTrend(String period, String startDate, String endDate) {
        var range = trendRangeSupport.resolve(period, startDate, endDate);
        try {
            String sql = "SELECT to_char(created_at, ?) as bucket, " +
                        "COALESCE(MAX(table_size), 0) as table_size, " +
                        "COALESCE(MAX(index_size), 0) as index_size " +
                        "FROM t_tablespace " +
                        "WHERE created_at >= ? AND created_at < ? " +
                        "GROUP BY bucket ORDER BY bucket ASC";

            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql,
                    range.bucketFormat(), range.startTime(), range.endTime());

            Map<String, Map<String, Object>> byBucket = new LinkedHashMap<>();
            for (Map<String, Object> row : rows) {
                long tableSize = ((Number) row.getOrDefault("table_size", 0L)).longValue();
                long indexSize = ((Number) row.getOrDefault("index_size", 0L)).longValue();

                Map<String, Object> trend = new LinkedHashMap<>();
                trend.put("tableSize", formatBytes(tableSize));
                trend.put("tableSizeBytes", tableSize);
                trend.put("indexSize", formatBytes(indexSize));
                trend.put("indexSizeBytes", indexSize);
                trend.put("totalSize", formatBytes(tableSize + indexSize));
                byBucket.put(String.valueOf(row.get("bucket")), trend);
            }

            List<Map<String, Object>> trendData = trendRangeSupport.fillBuckets(
                    range.buckets(), byBucket,
                    () -> Map.of("tableSize", "0 B", "tableSizeBytes", 0, "indexSize", "0 B", "indexSizeBytes", 0, "totalSize", "0 B"));

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("period", range.period());
            result.put("trendData", trendData);
            result.put("count", trendData.size());
            return result;
        } catch (Exception e) {
            log.error("查询表空间趋势失败：", e);
            return Map.of("period", range.period(), "trendData", List.of(), "count", 0);
        }
    }

    @Override
    public Map<String, Object> getSlowQueries(int page, int size, String dbName) {
        try {
            StringBuilder sql = new StringBuilder(
                "SELECT " +
                "id, db_name, query_sql, " +
                "execution_time, lock_time, rows_examined, " +
                "created_at " +
                "FROM t_db_slowquery " +
                "WHERE 1=1"
            );
            List<Object> params = new ArrayList<>();
            
            if (dbName != null && !dbName.trim().isEmpty()) {
                sql.append(" AND db_name = ?");
                params.add(dbName);
            }
            
            sql.append(" ORDER BY execution_time DESC LIMIT ? OFFSET ?");
            params.add(size);
            params.add((page - 1) * size);
            
            List<Map<String, Object>> content = jdbcTemplate.queryForList(sql.toString(), params.toArray());
            
            // 获取总数
            StringBuilder countSql = new StringBuilder("SELECT COUNT(*) as total FROM t_db_slowquery WHERE 1=1");
            List<Object> countParams = new ArrayList<>();
            
            if (dbName != null && !dbName.trim().isEmpty()) {
                countSql.append(" AND db_name = ?");
                countParams.add(dbName);
            }
            
            Integer total = jdbcTemplate.queryForObject(countSql.toString(), Integer.class, countParams.toArray());
            if (total == null) total = 0;
            
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("content", content);
            result.put("totalElements", total);
            result.put("totalPages", (total + size - 1) / size);
            result.put("currentPage", page);
            result.put("pageSize", size);
            
            return result;
        } catch (Exception e) {
            log.error("查询慢查询失败：", e);
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
    public Map<String, Object> getTableSchema(String dbName, String tableName) {
        try {
            // PostgreSQL: 查询表的列信息
            String sql = "SELECT " +
                        "column_name, data_type, is_nullable, " +
                        "column_default " +
                        "FROM information_schema.columns " +
                        "WHERE table_schema = 'public' AND table_name = ? " +
                        "ORDER BY ordinal_position";
            
            List<Map<String, Object>> columns = jdbcTemplate.queryForList(sql, tableName);
            
            if (columns.isEmpty()) {
                return Map.of("found", false, "message", "表不存在");
            }
            
            // PostgreSQL: 查询表的索引信息
            String indexSql = "SELECT " +
                             "indexname, indexdef " +
                             "FROM pg_indexes " +
                             "WHERE schemaname = 'public' AND tablename = ? " +
                             "ORDER BY indexname";
            
            List<Map<String, Object>> indexes = jdbcTemplate.queryForList(indexSql, tableName);
            
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("found", true);
            result.put("dbName", dbName);
            result.put("tableName", tableName);
            result.put("columns", columns);
            result.put("indexes", indexes);
            result.put("columnCount", columns.size());
            
            return result;
        } catch (Exception e) {
            log.error("查询表 Schema 失败：", e);
            return Map.of("found", false, "error", e.getMessage());
        }
    }

    @Override
    public Map<String, Object> getTableData(String dbName, String tableName, int page, int size, String whereClause) {
        try {
            // 构建 SQL (PostgreSQL: 使用 schema.table 或直接表名)
            String baseSql = "SELECT * FROM " + tableName;
            String querySql = baseSql;
            
            if (whereClause != null && !whereClause.trim().isEmpty()) {
                querySql += " WHERE " + whereClause;
            }
            
            querySql += " LIMIT ? OFFSET ?";
            
            List<Map<String, Object>> content = jdbcTemplate.queryForList(
                querySql,
                size,
                (page - 1) * size
            );
            
            // 获取总数
            String countSql = "SELECT COUNT(*) as total FROM " + tableName;
            if (whereClause != null && !whereClause.trim().isEmpty()) {
                countSql += " WHERE " + whereClause;
            }
            
            Integer total = jdbcTemplate.queryForObject(countSql, Integer.class);
            if (total == null) total = 0;
            
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("content", content);
            result.put("totalElements", total);
            result.put("totalPages", (total + size - 1) / size);
            result.put("currentPage", page);
            result.put("pageSize", size);
            result.put("dbName", dbName);
            result.put("tableName", tableName);
            
            return result;
        } catch (Exception e) {
            log.error("查询表数据失败：", e);
            return Map.of(
                "content", List.of(),
                "totalElements", 0,
                "totalPages", 0,
                "currentPage", page,
                "pageSize", size,
                "error", e.getMessage()
            );
        }
    }

    @Override
    public Map<String, Object> getDbInfo() {
        try {
            // PostgreSQL: 查询数据库基本信息
            String sql = "SELECT " +
                        "schemaname as table_schema, " +
                        "COUNT(*) as table_count " +
                        "FROM pg_tables " +
                        "WHERE schemaname NOT IN ('pg_catalog', 'information_schema', 'pg_toast') " +
                        "GROUP BY schemaname";
            
            List<Map<String, Object>> databases = jdbcTemplate.queryForList(sql);
            
            // 获取总表数
            String totalSql = "SELECT COUNT(*) as total_tables FROM pg_tables WHERE schemaname NOT IN ('pg_catalog', 'information_schema', 'pg_toast')";
            Integer totalTables = jdbcTemplate.queryForObject(totalSql, Integer.class);
            
            // 获取数据库大小
            String sizeSql = "SELECT pg_size_pretty(pg_database_size(current_database())) as db_size";
            Map<String, Object> sizeResult = jdbcTemplate.queryForList(sizeSql).stream().findFirst().orElse(Map.of());
            
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("databases", databases);
            result.put("databaseCount", databases.size());
            result.put("totalTables", totalTables != null ? totalTables : 0);
            result.put("databaseSize", sizeResult.getOrDefault("db_size", "unknown"));
            result.put("currentDatabase", "zuo_ai_agent");
            
            return result;
        } catch (Exception e) {
            log.error("查询数据库信息失败：", e);
            return Map.of("databases", List.of(), "databaseCount", 0, "error", e.getMessage());
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
