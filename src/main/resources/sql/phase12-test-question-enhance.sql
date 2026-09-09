-- Phase 12: 测试题目增强 - 结构化意图/知识库/文档关联
-- 创建时间: 2026-09-09

-- 新增字段：期望意图节点 ID（结构化，替代 expected_intent 文本）
ALTER TABLE t_rag_test_question ADD COLUMN IF NOT EXISTS expected_intent_node_id BIGINT NULL COMMENT '期望意图节点 ID';

-- 新增字段：期望知识库 ID
ALTER TABLE t_rag_test_question ADD COLUMN IF NOT EXISTS expected_kb_id BIGINT NULL COMMENT '期望知识库 ID';

-- 注意：standard_answer 字段已在 phase11 中创建，无需新增
