-- Phase 12: 实验计划支持知识库选择
-- 作者: 浩浩
-- 创建时间: 2026-09-06
-- 说明: 给实验计划表添加 knowledge_base_ids 字段，支持实验时指定知识库范围

-- 1. 给 t_rag_experiment_plan 表添加 knowledge_base_ids 字段
ALTER TABLE t_rag_experiment_plan
    ADD COLUMN knowledge_base_ids VARCHAR(500) DEFAULT NULL COMMENT '关联的知识库ID列表（逗号分隔）' AFTER use_custom_questions;

-- 2. 添加索引（可选，便于查询）
-- CREATE INDEX idx_plan_kb_ids ON t_rag_experiment_plan(knowledge_base_ids);
