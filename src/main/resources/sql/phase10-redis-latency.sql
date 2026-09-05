-- Phase 10: Redis 延迟监控表
-- 由 MonitorDataCollector.collectRedisLatency() 每分钟探测写入

CREATE TABLE IF NOT EXISTS t_redis_latency (
    id BIGSERIAL PRIMARY KEY,
    latency_ms BIGINT DEFAULT 0,
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_redis_latency_time ON t_redis_latency (created_at DESC);
