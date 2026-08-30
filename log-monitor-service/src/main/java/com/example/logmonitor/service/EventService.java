package com.example.logmonitor.service;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.logmonitor.entity.EventDO;
import com.example.logmonitor.mapper.EventMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class EventService {

    private final EventMapper eventMapper;
    private final JdbcTemplate jdbcTemplate;

    public Page<EventDO> search(String eventType, String serviceName,
                                 LocalDateTime startTime, LocalDateTime endTime,
                                 int current, int size) {
        LambdaQueryWrapper<EventDO> wrapper = new LambdaQueryWrapper<>();
        if (eventType != null) wrapper.eq(EventDO::getEventType, eventType);
        if (serviceName != null) wrapper.eq(EventDO::getServiceName, serviceName);
        if (startTime != null) wrapper.ge(EventDO::getEventTime, startTime);
        if (endTime != null) wrapper.le(EventDO::getEventTime, endTime);
        wrapper.orderByDesc(EventDO::getEventTime);
        return eventMapper.selectPage(new Page<>(current, size), wrapper);
    }

    public Map<String, Object> getOverview(int hours) {
        Map<String, Object> result = new LinkedHashMap<>();
        LocalDateTime since = LocalDateTime.now().minusHours(hours);

        String countSql = "SELECT COUNT(*) FROM t_event WHERE event_time >= ?";
        Long totalEvents = jdbcTemplate.queryForObject(countSql, Long.class, since);
        result.put("totalEvents", totalEvents != null ? totalEvents : 0);

        String typeSql = "SELECT event_type, COUNT(*) as cnt FROM t_event " +
                        "WHERE event_time >= ? GROUP BY event_type ORDER BY cnt DESC";
        List<Map<String, Object>> typeStats = jdbcTemplate.queryForList(typeSql, since);
        result.put("typeStats", typeStats);

        String svcSql = "SELECT service_name, COUNT(*) as cnt FROM t_event " +
                       "WHERE event_time >= ? GROUP BY service_name ORDER BY cnt DESC";
        List<Map<String, Object>> serviceStats = jdbcTemplate.queryForList(svcSql, since);
        result.put("serviceStats", serviceStats);

        return result;
    }

    public List<Map<String, Object>> getTrend(int hours) {
        String sql = "SELECT date_trunc('hour', event_time) as hour_bucket, " +
                    "event_type, COUNT(*) as count " +
                    "FROM t_event WHERE event_time >= ? " +
                    "GROUP BY hour_bucket, event_type ORDER BY hour_bucket ASC";
        return jdbcTemplate.queryForList(sql, LocalDateTime.now().minusHours(hours));
    }
}
