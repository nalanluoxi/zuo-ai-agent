-- Phase 7: trace/event/heartbeat + inverted index log aggregation
-- Trace spans
CREATE TABLE IF NOT EXISTS t_trace_span (
    id BIGINT PRIMARY KEY,
    trace_id VARCHAR(64) NOT NULL,
    span_id VARCHAR(32) NOT NULL,
    parent_span_id VARCHAR(32),
    operation_name VARCHAR(256),
    service_name VARCHAR(64),
    start_time TIMESTAMP NOT NULL,
    duration_ms DOUBLE PRECISION,
    status VARCHAR(16) DEFAULT 'OK',
    tags TEXT,
    create_time TIMESTAMP DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_trace_span_trace_id ON t_trace_span (trace_id);
CREATE INDEX IF NOT EXISTS idx_trace_span_service ON t_trace_span (service_name, start_time DESC);
CREATE INDEX IF NOT EXISTS idx_trace_span_start ON t_trace_span (start_time DESC);
CREATE INDEX IF NOT EXISTS idx_trace_span_status ON t_trace_span (status);

-- Events
CREATE TABLE IF NOT EXISTS t_event (
    id BIGINT PRIMARY KEY,
    event_type VARCHAR(32) NOT NULL,
    source VARCHAR(128),
    message TEXT,
    service_name VARCHAR(64),
    host VARCHAR(128),
    event_time TIMESTAMP NOT NULL,
    metadata TEXT,
    create_time TIMESTAMP DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_event_type ON t_event (event_type, event_time DESC);
CREATE INDEX IF NOT EXISTS idx_event_service ON t_event (service_name, event_time DESC);
CREATE INDEX IF NOT EXISTS idx_event_time ON t_event (event_time DESC);

-- Heartbeats
CREATE TABLE IF NOT EXISTS t_heartbeat (
    id BIGINT PRIMARY KEY,
    service_name VARCHAR(64) NOT NULL,
    host VARCHAR(128) NOT NULL,
    status VARCHAR(16) DEFAULT 'HEALTHY',
    cpu_usage DOUBLE PRECISION,
    memory_usage DOUBLE PRECISION,
    active_threads INT,
    gc_count BIGINT,
    heartbeat_time TIMESTAMP NOT NULL,
    create_time TIMESTAMP DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_heartbeat_service ON t_heartbeat (service_name, heartbeat_time DESC);
CREATE INDEX IF NOT EXISTS idx_heartbeat_host ON t_heartbeat (host, heartbeat_time DESC);
CREATE INDEX IF NOT EXISTS idx_heartbeat_time ON t_heartbeat (heartbeat_time DESC);

-- Inverted index for log token aggregation (hourly/minute buckets)
CREATE TABLE IF NOT EXISTS t_log_token (
    id BIGINT PRIMARY KEY,
    token VARCHAR(128) NOT NULL,
    service_name VARCHAR(64),
    log_level VARCHAR(8),
    time_bucket TIMESTAMP NOT NULL,
    doc_count BIGINT DEFAULT 0,
    create_time TIMESTAMP DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_log_token_token ON t_log_token (token, time_bucket DESC);
CREATE INDEX IF NOT EXISTS idx_log_token_service ON t_log_token (service_name, time_bucket DESC);
CREATE INDEX IF NOT EXISTS idx_log_token_bucket ON t_log_token (time_bucket DESC);
