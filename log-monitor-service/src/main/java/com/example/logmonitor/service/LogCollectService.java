package com.example.logmonitor.service;

import cn.hutool.core.lang.Snowflake;
import cn.hutool.core.util.IdUtil;
import com.example.logmonitor.entity.AppLogDO;
import com.example.logmonitor.mapper.AppLogMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class LogCollectService {

    private static final Logger log = LoggerFactory.getLogger(LogCollectService.class);
    private static final Snowflake snowflake = IdUtil.getSnowflake(1, 4);

    private final AppLogMapper appLogMapper;

    @SuppressWarnings("unchecked")
    public void collectBatch(List<Map<String, Object>> batch) {
        try {
            for (Map<String, Object> entry : batch) {
                AppLogDO appLog = new AppLogDO();
                appLog.setId(snowflake.nextId());
                appLog.setServiceName((String) entry.getOrDefault("serviceName", "unknown"));
                appLog.setHostName((String) entry.getOrDefault("hostName", ""));
                appLog.setTraceId((String) entry.getOrDefault("traceId", ""));
                appLog.setLogLevel((String) entry.getOrDefault("logLevel", "INFO"));
                appLog.setLoggerName((String) entry.getOrDefault("loggerName", ""));
                appLog.setThreadName((String) entry.getOrDefault("threadName", ""));
                appLog.setMessage((String) entry.getOrDefault("message", ""));
                appLog.setStackTrace((String) entry.getOrDefault("stackTrace", null));
                appLog.setLogTs(LocalDateTime.now());
                appLog.setCreateTime(LocalDateTime.now());
                appLogMapper.insert(appLog);
            }
            log.debug("批量收集日志 {} 条", batch.size());
        } catch (Exception e) {
            log.warn("日志批量写入失败: {}", e.getMessage());
        }
    }
}