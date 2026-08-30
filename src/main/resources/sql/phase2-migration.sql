-- Phase 2: P6 对话接口支持 + P12-P14 监控功能表

-- ===================== P6: 对话表 =====================

-- 对话列表表
CREATE TABLE IF NOT EXISTS t_conversation (
    id VARCHAR(64) PRIMARY KEY,
    title VARCHAR(256),
    tenant_id BIGINT,
    user_id BIGINT,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW(),
    deleted SMALLINT DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_conversation_tenant_user 
ON t_conversation (tenant_id, user_id, created_at DESC);

-- 聊天消息原始表（补充字段）
-- t_chat_message_raw 已在 phase1-migration.sql 中创建，这里补充 tenant_id 字段
ALTER TABLE t_chat_message_raw 
ADD COLUMN IF NOT EXISTS tenant_id BIGINT;

ALTER TABLE t_chat_message_raw 
ADD COLUMN IF NOT EXISTS user_id BIGINT;

CREATE INDEX IF NOT EXISTS idx_raw_tenant 
ON t_chat_message_raw (tenant_id, conversation_id, create_time DESC);

-- ===================== P12-P14: 监控表 =====================

-- Redis 监控数据表（QPS、热门 key 统计）
CREATE TABLE IF NOT EXISTS t_monitor_redis_stats (
    id BIGINT PRIMARY KEY,
    metric_type VARCHAR(32) NOT NULL,  -- 'qps', 'memory', 'hotkey' 等
    key_name VARCHAR(256),
    value DOUBLE,
    sample_time TIMESTAMP DEFAULT NOW(),
    tenant_id BIGINT,
    INDEX idx_redis_metric_time (metric_type, sample_time DESC),
    INDEX idx_redis_tenant (tenant_id, sample_time DESC)
);

-- 数据库表空间监控表
CREATE TABLE IF NOT EXISTS t_monitor_table_space (
    id BIGINT PRIMARY KEY,
    database_name VARCHAR(128),
    schema_name VARCHAR(128),
    table_name VARCHAR(256),
    row_count BIGINT,
    size_bytes BIGINT,
    size_pretty VARCHAR(32),
    sample_time TIMESTAMP DEFAULT NOW(),
    tenant_id BIGINT,
    INDEX idx_table_space_time (table_name, sample_time DESC),
    INDEX idx_table_space_tenant (tenant_id, sample_time DESC)
);

-- 监控指标历史表（时间序列数据）
CREATE TABLE IF NOT EXISTS t_monitor_metrics (
    id BIGINT PRIMARY KEY,
    metric_name VARCHAR(128) NOT NULL,  -- 'redis.qps', 'db.tables.count' 等
    metric_value DOUBLE,
    tags TEXT,  -- JSON 格式 {"tenant_id": 1, "key": "..."}
    timestamp BIGINT,  -- 毫秒时间戳
    created_at TIMESTAMP DEFAULT NOW(),
    INDEX idx_metrics_name_time (metric_name, timestamp DESC),
    INDEX idx_metrics_tenant (tags)
);

-- 监控告警规则表
CREATE TABLE IF NOT EXISTS t_monitor_alert_rule (
    id BIGINT PRIMARY KEY,
    rule_name VARCHAR(128),
    metric_name VARCHAR(128),
    threshold DOUBLE,
    comparison_type VARCHAR(16),  -- '>', '<', '=', etc
    alert_message VARCHAR(512),
    enabled SMALLINT DEFAULT 1,
    tenant_id BIGINT,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW(),
    INDEX idx_alert_rule_tenant (tenant_id),
    INDEX idx_alert_rule_enabled (enabled)
);

-- 监控告警历史表
CREATE TABLE IF NOT EXISTS t_monitor_alert_history (
    id BIGINT PRIMARY KEY,
    rule_id BIGINT,
    alert_level VARCHAR(16),  -- 'info', 'warning', 'critical'
    alert_message VARCHAR(512),
    metric_value DOUBLE,
    is_resolved SMALLINT DEFAULT 0,
    resolved_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT NOW(),
    tenant_id BIGINT,
    INDEX idx_alert_history_resolved (is_resolved),
    INDEX idx_alert_history_tenant (tenant_id, created_at DESC)
);

-- ===================== P19-P21: 多租户隔离补全 =====================

-- 为意图节点表添加 tenant_id（如果不存在）
ALTER TABLE t_intent_node 
ADD COLUMN IF NOT EXISTS tenant_id BIGINT;

CREATE INDEX IF NOT EXISTS idx_intent_node_tenant 
ON t_intent_node (tenant_id, parent_id);

-- 为知识库文档表补充租户隔离
ALTER TABLE t_knowledge_document 
ADD COLUMN IF NOT EXISTS tenant_id BIGINT;

CREATE INDEX IF NOT EXISTS idx_kb_document_tenant 
ON t_knowledge_document (tenant_id, kb_id);

-- 权限申请表添加租户隔离
ALTER TABLE t_approval 
ADD COLUMN IF NOT EXISTS tenant_id BIGINT;

ALTER TABLE t_approval 
ADD COLUMN IF NOT EXISTS status_updated_at TIMESTAMP;

CREATE INDEX IF NOT EXISTS idx_approval_tenant 
ON t_approval (tenant_id, status, apply_time DESC);

-- 资源访问权限表添加租户隔离
ALTER TABLE t_resource_access 
ADD COLUMN IF NOT EXISTS tenant_id BIGINT;

CREATE INDEX IF NOT EXISTS idx_resource_access_tenant 
ON t_resource_access (tenant_id, resource_type, grantee_type);

-- ===================== 初始化数据 =====================

-- 为 t_team 表补充 parentId 索引（树形查询优化）
CREATE INDEX IF NOT EXISTS idx_team_parent 
ON t_team (parent_id, deleted);

CREATE INDEX IF NOT EXISTS idx_team_tenant 
ON t_team (tenant_id, deleted);

-- 审计日志表
ALTER TABLE t_audit_log 
ADD COLUMN IF NOT EXISTS tenant_id BIGINT;

CREATE INDEX IF NOT EXISTS idx_audit_log_tenant 
ON t_audit_log (tenant_id, create_time DESC);
