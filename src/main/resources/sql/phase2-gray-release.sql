-- ============================================================
-- Phase 2: 灰度发布计划系统
-- 创建时间: 2026-09-06
-- 作者: 浩浩
-- ============================================================

-- 1. 发布计划表
CREATE TABLE IF NOT EXISTS t_gray_release_plan (
    id BIGSERIAL PRIMARY KEY,
    plan_name VARCHAR(100) NOT NULL,

    -- 发布对象
    component_type VARCHAR(50) NOT NULL,  -- 'config' / 'prompt' / 'model'
    component_id BIGINT NOT NULL,
    from_version_id BIGINT NOT NULL,      -- 当前版本（回滚目标）
    to_version_id BIGINT NOT NULL,        -- 目标版本

    -- 灰度策略（从 t_rag_config 迁出）
    gray_ratio DOUBLE PRECISION DEFAULT 0.1,
    gray_mode VARCHAR(20) DEFAULT 'PERCENT',  -- PERCENT/LIST/BOTH
    gray_user_ids TEXT,
    gray_duration_hours INT DEFAULT 24,

    -- 状态机
    status VARCHAR(20) DEFAULT 'DRAFT',   -- DRAFT/APPROVED/GRAYING/ACTIVE/ROLLED_BACK/REJECTED
    approved_by BIGINT,
    approved_time TIMESTAMP,

    -- 灰度指标快照
    gray_metrics_snapshot TEXT,

    -- 元数据
    change_log VARCHAR(500),
    create_user_id BIGINT,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    finish_time TIMESTAMP
);

-- 2. 配置表新增 status 字段
ALTER TABLE t_rag_config ADD COLUMN IF NOT EXISTS status VARCHAR(20) DEFAULT 'DRAFT';

-- 3. 提示词表新增 status 字段
ALTER TABLE t_rag_prompt_template ADD COLUMN IF NOT EXISTS status VARCHAR(20) DEFAULT 'DRAFT';
