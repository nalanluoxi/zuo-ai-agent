package com.example.logmonitor.service;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.logmonitor.entity.HeartbeatDO;
import com.example.logmonitor.mapper.HeartbeatMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class HeartbeatService {

    private final HeartbeatMapper heartbeatMapper;
    private final JdbcTemplate jdbcTemplate;

    public Page<HeartbeatDO> search(String serviceName, String host,
                                     LocalDateTime startTime, LocalDateTime endTime,
                                     int current, int size) {
        LambdaQueryWrapper<HeartbeatDO> wrapper = new LambdaQueryWrapper<>();
        if (serviceName != null) wrapper.eq(HeartbeatDO::getServiceName, serviceName);
        if (host != null) wrapper.eq(HeartbeatDO::getHost, host);
        if (startTime != null) wrapper.ge(HeartbeatDO::getHeartbeatTime, startTime);
        if (endTime != null) wrapper.le(HeartbeatDO::getHeartbeatTime, endTime);
        wrapper.orderByDesc(HeartbeatDO::getHeartbeatTime);
        return heartbeatMapper.selectPage(new Page<>(current, size), wrapper);
    }

    /** 获取各服务最新状态 */
    public Map<String, Object> getStatusBoard() {
        Map<String, Object> result = new LinkedHashMap<>();

        // 每个服务最新心跳
        String sql = "SELECT DISTINCT ON (service_name) " +
                    "service_name, host, status, cpu_usage, memory_usage, " +
                    "active_threads, gc_count, heartbeat_time " +
                    "FROM t_heartbeat " +
                    "ORDER BY service_name, heartbeat_time DESC";
        List<Map<String, Object>> services = jdbcTemplate.queryForList(sql);
        result.put("services", services);

        // 健康/异常统计
        int healthy = 0, unhealthy = 0;
        for (Map<String, Object> s : services) {
            String status = (String) s.get("status");
            if ("HEALTHY".equalsIgnoreCase(status)) healthy++;
            else unhealthy++;
        }
        result.put("healthyCount", healthy);
        result.put("unhealthyCount", unhealthy);

        return result;
    }

    /** 获取指定服务的心跳趋势 */
    public List<Map<String, Object>> getTrend(String serviceName, int hours) {
        String sql = "SELECT date_trunc('hour', heartbeat_time) as hour_bucket, " +
                    "AVG(cpu_usage) as avg_cpu, AVG(memory_usage) as avg_memory, " +
                    "MAX(cpu_usage) as max_cpu, MAX(memory_usage) as max_memory " +
                    "FROM t_heartbeat WHERE service_name = ? AND heartbeat_time >= ? " +
                    "GROUP BY hour_bucket ORDER BY hour_bucket ASC";
        return jdbcTemplate.queryForList(sql, serviceName, LocalDateTime.now().minusHours(hours));
    }
}
