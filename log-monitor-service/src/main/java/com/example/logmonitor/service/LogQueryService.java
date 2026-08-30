package com.example.logmonitor.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.logmonitor.entity.AppLogDO;
import com.example.logmonitor.mapper.AppLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class LogQueryService {

    private final AppLogMapper appLogMapper;

    public Page<AppLogDO> search(String keyword, String service, String level,
                                  LocalDateTime startTime, LocalDateTime endTime,
                                  String traceId, int current, int size) {
        LambdaQueryWrapper<AppLogDO> qw = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            qw.and(w -> w.like(AppLogDO::getMessage, keyword).or().like(AppLogDO::getStackTrace, keyword));
        }
        if (service != null && !service.isBlank()) qw.eq(AppLogDO::getServiceName, service);
        if (level != null && !level.isBlank()) qw.eq(AppLogDO::getLogLevel, level);
        if (traceId != null && !traceId.isBlank()) qw.eq(AppLogDO::getTraceId, traceId);
        if (startTime != null) qw.ge(AppLogDO::getLogTs, startTime);
        if (endTime != null) qw.le(AppLogDO::getLogTs, endTime);
        qw.orderByDesc(AppLogDO::getLogTs);
        return appLogMapper.selectPage(new Page<>(current, size), qw);
    }

    public List<AppLogDO> traceByTraceId(String traceId) {
        return appLogMapper.selectList(new LambdaQueryWrapper<AppLogDO>()
                .eq(AppLogDO::getTraceId, traceId).orderByAsc(AppLogDO::getLogTs));
    }

    public Map<String, Long> getLogStats(LocalDateTime since) {
        Map<String, Long> stats = new LinkedHashMap<>();
        List<Map<String, Object>> rows = appLogMapper.selectMaps(new LambdaQueryWrapper<AppLogDO>()
                .ge(AppLogDO::getLogTs, since)
                .select(AppLogDO::getLogLevel, AppLogDO::getServiceName));
        for (Map<String, Object> row : rows) {
            stats.merge("total", 1L, Long::sum);
            stats.merge("level_" + row.get("logLevel"), 1L, Long::sum);
            stats.merge("service_" + row.get("serviceName"), 1L, Long::sum);
        }
        return stats;
    }
}