-- Phase 5: Dashboard 统计所需表

-- Token 使用记录表
CREATE TABLE IF NOT EXISTS t_token_usage (
    id BIGSERIAL PRIMARY KEY,
    conversation_id VARCHAR(64) NOT NULL,
    message_id VARCHAR(64) NOT NULL,
    user_id BIGINT,
    tenant_id BIGINT,
    input_tokens INTEGER NOT NULL DEFAULT 0,
    output_tokens INTEGER NOT NULL DEFAULT 0,
    total_tokens INTEGER NOT NULL DEFAULT 0,
    model VARCHAR(64),
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_token_usage_conversation 
ON t_token_usage (conversation_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_token_usage_user 
ON t_token_usage (user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_token_usage_date 
ON t_token_usage (DATE(created_at));

-- 检索日志表（用于命中率统计）
CREATE TABLE IF NOT EXISTS t_retrieval_log (
    id BIGSERIAL PRIMARY KEY,
    conversation_id VARCHAR(64),
    message_id VARCHAR(64),
    user_id BIGINT,
    tenant_id BIGINT,
    knowledge_base_id VARCHAR(64),
    query_text TEXT,
    result_count INTEGER DEFAULT 0,
    relevance_score DECIMAL(3,2),  -- 0.00 - 1.00
    latency_ms INTEGER,
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_retrieval_log_conversation 
ON t_retrieval_log (conversation_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_retrieval_log_user 
ON t_retrieval_log (user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_retrieval_log_date 
ON t_retrieval_log (DATE(created_at));

CREATE INDEX IF NOT EXISTS idx_retrieval_log_kb 
ON t_retrieval_log (knowledge_base_id, created_at DESC);
