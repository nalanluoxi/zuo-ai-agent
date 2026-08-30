-- Phase 3: 全文检索 + 长期记忆
ALTER TABLE t_knowledge_document ADD COLUMN IF NOT EXISTS search_vector tsvector;
CREATE INDEX IF NOT EXISTS idx_doc_search ON t_knowledge_document USING GIN (search_vector);

-- Phase 3.1: 意图树多租户支持
ALTER TABLE t_intent_node ADD COLUMN IF NOT EXISTS tenant_id BIGINT;
CREATE INDEX IF NOT EXISTS idx_intent_node_tenant_id ON t_intent_node (tenant_id);

CREATE TABLE IF NOT EXISTS t_user_memory_profile (
    id BIGINT PRIMARY KEY, user_id BIGINT UNIQUE, memory_json TEXT,
    create_time TIMESTAMP DEFAULT NOW(), update_time TIMESTAMP DEFAULT NOW()
);

ALTER TABLE t_rag_trace_node ADD COLUMN IF NOT EXISTS prompt_tokens INT DEFAULT 0;
ALTER TABLE t_rag_trace_node ADD COLUMN IF NOT EXISTS completion_tokens INT DEFAULT 0;