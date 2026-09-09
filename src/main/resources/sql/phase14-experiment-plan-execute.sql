-- Phase 14: 实验计划执行关联实验 ID
-- 作者: 浩浩
-- 创建时间: 2026-09-09
-- 说明: 给实验计划表添加 experiment_id 字段，关联实际执行的实验记录，
--       同时给 RUNNING 状态的计划补一个取消重置接口所需的索引

-- 1. 给 t_rag_experiment_plan 表添加 experiment_id 字段
ALTER TABLE t_rag_experiment_plan
    ADD COLUMN experiment_id BIGINT DEFAULT NULL;
COMMENT ON COLUMN t_rag_experiment_plan.experiment_id IS '关联的实验记录 ID（t_rag_experiment.id）';

-- 2. 添加索引（便于通过 experiment_id 反查计划）
CREATE INDEX idx_plan_experiment_id ON t_rag_experiment_plan(experiment_id);
