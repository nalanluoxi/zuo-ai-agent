-- Phase 3a: 补齐 t_knowledge_base 缺失列
ALTER TABLE t_knowledge_base
    ADD COLUMN IF NOT EXISTS tenant_id BIGINT DEFAULT 1,
    ADD COLUMN IF NOT EXISTS team_id BIGINT DEFAULT 0,
    ADD COLUMN IF NOT EXISTS owner_id BIGINT DEFAULT 0,
    ADD COLUMN IF NOT EXISTS visibility VARCHAR(16) DEFAULT 'PRIVATE',
    ADD COLUMN IF NOT EXISTS enabled INTEGER DEFAULT 1,
    ADD COLUMN IF NOT EXISTS doc_count INTEGER DEFAULT 0,
    ADD COLUMN IF NOT EXISTS chunk_count INTEGER DEFAULT 0;

-- 添加索引
CREATE INDEX IF NOT EXISTS idx_kb_tenant ON t_knowledge_base(tenant_id, deleted);

-- Phase 3b: 补齐 t_team 缺失的 parent_id 列
ALTER TABLE t_team
    ADD COLUMN IF NOT EXISTS parent_id BIGINT DEFAULT 0;

CREATE INDEX IF NOT EXISTS idx_team_parent ON t_team(parent_id);

-- 添加 team_name 字段（如果不存在）
ALTER TABLE t_team
    ADD COLUMN IF NOT EXISTS team_name VARCHAR(128);
