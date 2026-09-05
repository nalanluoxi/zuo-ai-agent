-- Phase 8: 监控功能表（DB + Redis）

-- ===================== DB 监控表 =====================

-- DB QPS 监控表
CREATE TABLE IF NOT EXISTS t_db_qps (
    id BIGSERIAL PRIMARY KEY,
    query_count BIGINT DEFAULT 0,
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_db_qps_time ON t_db_qps (created_at DESC);

-- DB 访问流量监控表
CREATE TABLE IF NOT EXISTS t_db_access (
    id BIGSERIAL PRIMARY KEY,
    read_count BIGINT DEFAULT 0,
    write_count BIGINT DEFAULT 0,
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_db_access_time ON t_db_access (created_at DESC);

-- 表空间监控表
CREATE TABLE IF NOT EXISTS t_tablespace (
    id BIGSERIAL PRIMARY KEY,
    table_name VARCHAR(256),
    table_size BIGINT DEFAULT 0,
    index_size BIGINT DEFAULT 0,
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_tablespace_time ON t_tablespace (created_at DESC);

-- 慢查询记录表
CREATE TABLE IF NOT EXISTS t_db_slowquery (
    id BIGSERIAL PRIMARY KEY,
    db_name VARCHAR(128),
    query_sql TEXT,
    execution_time BIGINT DEFAULT 0,
    lock_time BIGINT DEFAULT 0,
    rows_examined BIGINT DEFAULT 0,
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_db_slowquery_time ON t_db_slowquery (created_at DESC);
CREATE INDEX idx_db_slowquery_exec_time ON t_db_slowquery (execution_time DESC);

-- ===================== Redis 监控表 =====================

-- Redis QPS 监控表
CREATE TABLE IF NOT EXISTS t_redis_qps (
    id BIGSERIAL PRIMARY KEY,
    qps BIGINT DEFAULT 0,
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_redis_qps_time ON t_redis_qps (created_at DESC);

-- Redis 内存监控表
CREATE TABLE IF NOT EXISTS t_redis_memory (
    id BIGSERIAL PRIMARY KEY,
    used_memory BIGINT DEFAULT 0,
    max_memory BIGINT DEFAULT 0,
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_redis_memory_time ON t_redis_memory (created_at DESC);

-- Redis Key 统计表
CREATE TABLE IF NOT EXISTS t_redis_key_stats (
    id BIGSERIAL PRIMARY KEY,
    key_name VARCHAR(256) NOT NULL,
    key_type VARCHAR(16),
    key_size BIGINT DEFAULT 0,
    access_count BIGINT DEFAULT 0,
    last_access_time TIMESTAMP,
    ttl BIGINT DEFAULT -1,
    expire_time TIMESTAMP,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_redis_key_stats_name ON t_redis_key_stats (key_name);
CREATE INDEX idx_redis_key_stats_size ON t_redis_key_stats (key_size DESC);
CREATE INDEX idx_redis_key_stats_access ON t_redis_key_stats (access_count DESC);
