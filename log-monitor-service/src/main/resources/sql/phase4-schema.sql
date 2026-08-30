-- Phase 4: log-monitor-service 表结构
CREATE TABLE IF NOT EXISTS t_app_log (
    id BIGINT PRIMARY KEY, service_name VARCHAR(64), host_name VARCHAR(128),
    trace_id VARCHAR(64), log_level VARCHAR(8), logger_name VARCHAR(256),
    thread_name VARCHAR(128), message TEXT, stack_trace TEXT,
    log_ts TIMESTAMP, create_time TIMESTAMP DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_app_log_level ON t_app_log (log_level, log_ts DESC);
CREATE INDEX IF NOT EXISTS idx_app_log_service ON t_app_log (service_name, log_ts DESC);
CREATE INDEX IF NOT EXISTS idx_app_log_trace ON t_app_log (trace_id);
CREATE INDEX IF NOT EXISTS idx_app_log_ts ON t_app_log (log_ts DESC);

CREATE TABLE IF NOT EXISTS t_alert_rule (
    id BIGINT PRIMARY KEY, rule_name VARCHAR(128), metric VARCHAR(64),
    condition VARCHAR(16), threshold DOUBLE PRECISION,
    window_minutes INT DEFAULT 5, notify_channels VARCHAR(256),
    enabled BOOLEAN DEFAULT true
);

CREATE TABLE IF NOT EXISTS t_alert_record (
    id BIGINT PRIMARY KEY, rule_id BIGINT, alert_level VARCHAR(16),
    alert_message TEXT, metric_value DOUBLE PRECISION,
    status VARCHAR(16) DEFAULT 'FIRED', fire_time TIMESTAMP,
    resolve_time TIMESTAMP
);

-- 种子数据：默认告警规则
INSERT INTO t_alert_rule (id, rule_name, metric, condition, threshold, window_minutes, notify_channels, enabled)
VALUES (1, '错误率过高', 'error_rate', 'GT', 0.1, 5, 'dingtalk', true)
ON CONFLICT DO NOTHING;