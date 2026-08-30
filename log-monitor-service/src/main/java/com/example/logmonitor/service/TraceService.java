package com.example.logmonitor.service;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.logmonitor.entity.TraceSpanDO;
import com.example.logmonitor.mapper.TraceSpanMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class TraceService {

    private final TraceSpanMapper traceSpanMapper;
    private final JdbcTemplate jdbcTemplate;

    public Page<TraceSpanDO> search(String serviceName, String status,
                                     LocalDateTime startTime, LocalDateTime endTime,
                                     int current, int size) {
        LambdaQueryWrapper<TraceSpanDO> wrapper = new LambdaQueryWrapper<>();
        if (serviceName != null) wrapper.eq(TraceSpanDO::getServiceName, serviceName);
        if (status != null) wrapper.eq(TraceSpanDO::getStatus, status);
        if (startTime != null) wrapper.ge(TraceSpanDO::getStartTime, startTime);
        if (endTime != null) wrapper.le(TraceSpanDO::getStartTime, endTime);
        wrapper.orderByDesc(TraceSpanDO::getStartTime);
        return traceSpanMapper.selectPage(new Page<>(current, size), wrapper);
    }

    public List<TraceSpanDO> getTrace(String traceId) {
        LambdaQueryWrapper<TraceSpanDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TraceSpanDO::getTraceId, traceId).orderByAsc(TraceSpanDO::getStartTime);
        return traceSpanMapper.selectList(wrapper);
    }

    /** 获取 trace 统计概览 */
    public Map<String, Object> getOverview(int hours) {
        Map<String, Object> result = new LinkedHashMap<>();
        LocalDateTime since = LocalDateTime.now().minusHours(hours);

        // 总请求数
        String countSql = "SELECT COUNT(*) FROM t_trace_span WHERE start_time >= ?";
        Long totalSpans = jdbcTemplate.queryForObject(countSql, Long.class, since);
        result.put("totalSpans", totalSpans != null ? totalSpans : 0);

        // 平均耗时
        String avgSql = "SELECT COALESCE(AVG(duration_ms), 0) FROM t_trace_span WHERE start_time >= ?";
        Double avgDuration = jdbcTemplate.queryForObject(avgSql, Double.class, since);
        result.put("avgDurationMs", avgDuration != null ? avgDuration : 0.0);

        // 错误率
        String errSql = "SELECT COUNT(*) FROM t_trace_span WHERE start_time >= ? AND status = 'ERROR'";
        Long errorCount = jdbcTemplate.queryForObject(errSql, Long.class, since);
        double errorRate = totalSpans != null && totalSpans > 0 ?
                (errorCount != null ? errorCount : 0) * 100.0 / totalSpans : 0.0;
        result.put("errorCount", errorCount != null ? errorCount : 0);
        result.put("errorRate", String.format("%.2f%%", errorRate));

        // 按服务统计
        String svcSql = "SELECT service_name, COUNT(*) as cnt, COALESCE(AVG(duration_ms), 0) as avg_ms " +
                       "FROM t_trace_span WHERE start_time >= ? GROUP BY service_name ORDER BY cnt DESC";
        List<Map<String, Object>> serviceStats = jdbcTemplate.queryForList(svcSql, since);
        result.put("serviceStats", serviceStats);

        return result;
    }

    /** 获取按小时的趋势数据 */
    public List<Map<String, Object>> getTrend(int hours) {
        String sql = "SELECT date_trunc('hour', start_time) as hour_bucket, " +
                    "COUNT(*) as count, " +
                    "COALESCE(AVG(duration_ms), 0) as avg_duration_ms, " +
                    "SUM(CASE WHEN status = 'ERROR' THEN 1 ELSE 0 END) as error_count " +
                    "FROM t_trace_span " +
                    "WHERE start_time >= ? " +
                    "GROUP BY hour_bucket ORDER BY hour_bucket ASC";
        return jdbcTemplate.queryForList(sql, LocalDateTime.now().minusHours(hours));
    }
}
