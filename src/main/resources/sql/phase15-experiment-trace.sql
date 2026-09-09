-- Phase 15: 实验计划关联 Trace 记录
-- 作者: 浩浩
-- 创建时间: 2026-09-09
-- 说明: 给 t_rag_trace_run 表添加 experiment_id 字段，关联实验计划

-- 1. 给 t_rag_trace_run 表添加 experiment_id 字段
ALTER TABLE t_rag_trace_run
    ADD COLUMN experiment_id BIGINT DEFAULT NULL;
COMMENT ON COLUMN t_rag_trace_run.experiment_id IS '关联的实验记录 ID（t_rag_experiment.id）';

-- 2. 添加索引（便于通过 experiment_id 查询实验的 Trace 记录）
CREATE INDEX idx_rag_trace_run_experiment_id ON t_rag_trace_run(experiment_id);
