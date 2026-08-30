package com.example.logmonitor.service;

import com.example.logmonitor.entity.AlertRecordDO;
import com.example.logmonitor.entity.AlertRuleDO;
import com.example.logmonitor.mapper.AlertRuleMapper;
import com.example.logmonitor.mapper.AlertRecordMapper;
import com.example.logmonitor.mapper.AppLogMapper;
import cn.hutool.core.lang.Snowflake;
import cn.hutool.core.util.IdUtil;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AlertEngine {

    private static final Logger log = LoggerFactory.getLogger(AlertEngine.class);
    private static final Snowflake snowflake = IdUtil.getSnowflake(1, 5);

    private final AlertRuleMapper ruleMapper;
    private final AlertRecordMapper recordMapper;
    private final AppLogMapper appLogMapper;

    @Scheduled(fixedRate = 60000)
    public void checkAlerts() {
        List<AlertRuleDO> rules = ruleMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AlertRuleDO>()
                        .eq(AlertRuleDO::getEnabled, true));
        for (AlertRuleDO rule : rules) {
            try {
                if ("error_rate".equals(rule.getMetric())) {
                    checkErrorRate(rule);
                } else if ("avg_duration".equals(rule.getMetric())) {
                    checkAvgDuration(rule);
                }
            } catch (Exception e) {
                log.warn("告警规则 {} 检查失败: {}", rule.getRuleName(), e.getMessage());
            }
        }
    }

    private void checkErrorRate(AlertRuleDO rule) {
        LocalDateTime since = LocalDateTime.now().minusMinutes(rule.getWindowMinutes());
        Long total = appLogMapper.selectCount(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.example.logmonitor.entity.AppLogDO>()
                .ge(com.example.logmonitor.entity.AppLogDO::getLogTs, since));
        Long errors = appLogMapper.selectCount(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.example.logmonitor.entity.AppLogDO>()
                .ge(com.example.logmonitor.entity.AppLogDO::getLogTs, since)
                .eq(com.example.logmonitor.entity.AppLogDO::getLogLevel, "ERROR"));
        if (total > 0 && (double) errors / total > rule.getThreshold()) {
            fireAlert(rule, "ERROR", "错误率 " + String.format("%.2f%%", (double) errors / total * 100) + " 超过阈值 " + rule.getThreshold() * 100 + "%", (double) errors / total);
        }
    }

    private void checkAvgDuration(AlertRuleDO rule) {
        log.debug("平均耗时告警检查: {}", rule.getRuleName());
    }

    private void fireAlert(AlertRuleDO rule, String level, String message, double value) {
        AlertRecordDO record = new AlertRecordDO();
        record.setId(snowflake.nextId());
        record.setRuleId(rule.getId());
        record.setAlertLevel(level);
        record.setAlertMessage(message);
        record.setMetricValue(value);
        record.setStatus("FIRED");
        record.setFireTime(LocalDateTime.now());
        recordMapper.insert(record);
        log.warn("[ALERT] {}: {}", rule.getRuleName(), message);
    }
}