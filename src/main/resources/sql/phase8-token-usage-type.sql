-- ============================================================
-- Phase 8: Token 消耗分类统计
-- 为 t_token_usage 表添加 usage_type 字段，支持按用途分类统计
-- ============================================================

-- 添加 usage_type 字段（对话/向量入库/检索）
ALTER TABLE t_token_usage ADD COLUMN IF NOT EXISTS usage_type VARCHAR(20) DEFAULT 'CONVERSATION';

-- 为 usage_type 添加索引，提升按类型过滤的查询性能
CREATE INDEX IF NOT EXISTS idx_token_usage_type ON t_token_usage(usage_type);

-- 说明：
-- usage_type 取值：
--   CONVERSATION - 对话 token 消耗（LLM 输入/输出）
--   EMBEDDING    - 向量入库 token 消耗（文本向量化）
--   RETRIEVAL    - 检索 token 消耗（改写/重排序等）
