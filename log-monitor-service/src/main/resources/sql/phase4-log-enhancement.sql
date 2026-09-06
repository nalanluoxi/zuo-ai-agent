-- ============================================================
-- Phase 4: 日志系统增强（CAT 风格 transaction/event）
-- 创建时间: 2026-09-06
-- 作者: 浩浩
-- ============================================================

-- 1. 扩展 t_app_log 表
ALTER TABLE t_app_log ADD COLUMN IF NOT EXISTS log_type VARCHAR(20) DEFAULT 'normal';
ALTER TABLE t_app_log ADD COLUMN IF NOT EXISTS span_id VARCHAR(64);
ALTER TABLE t_app_log ADD COLUMN IF NOT EXISTS parent_trace_id VARCHAR(64);
ALTER TABLE t_app_log ADD COLUMN IF NOT EXISTS event_type VARCHAR(100);
ALTER TABLE t_app_log ADD COLUMN IF NOT EXISTS node_id BIGINT;
ALTER TABLE t_app_log ADD COLUMN IF NOT EXISTS metadata TEXT;

-- 2. 新增索引
CREATE INDEX IF NOT EXISTS idx_app_log_type ON t_app_log (log_type, log_ts DESC);
CREATE INDEX IF NOT EXISTS idx_app_log_event_type ON t_app_log (event_type, log_ts DESC);
CREATE INDEX IF NOT EXISTS idx_app_log_node_id ON t_app_log (node_id);
CREATE INDEX IF NOT EXISTS idx_app_log_parent_trace ON t_app_log (parent_trace_id);
