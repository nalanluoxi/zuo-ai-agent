-- ============================================================
-- Phase 5 + 6: 实验计划 + 生产数据回放
-- 创建时间: 2026-09-06
-- 作者: 浩浩
-- ============================================================

-- 1. 实验计划表
CREATE TABLE IF NOT EXISTS t_rag_experiment_plan (
    id BIGSERIAL PRIMARY KEY,
    plan_name VARCHAR(100) NOT NULL,

    -- 三变量控制
    model_config_id BIGINT,               -- 本次实验用的模型
    config_version_id BIGINT,             -- 本次实验用的配置版本
    prompt_version_id BIGINT,             -- 本次实验用的提示词版本

    -- 测试集
    use_global_questions BOOLEAN DEFAULT true,
    use_custom_questions BOOLEAN DEFAULT false,

    -- 状态
    status VARCHAR(20) DEFAULT 'PENDING', -- PENDING / RUNNING / COMPLETED / FAILED
    result_summary TEXT,                   -- 执行结果摘要（JSON）

    create_user_id BIGINT,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    finish_time TIMESTAMP
);

-- 2. 实验计划关联的全局题库（勾选）
CREATE TABLE IF NOT EXISTS t_rag_plan_question_ref (
    id BIGSERIAL PRIMARY KEY,
    plan_id BIGINT NOT NULL,
    question_id BIGINT NOT NULL           -- t_rag_test_question.id
);

CREATE INDEX IF NOT EXISTS idx_plan_question_ref_plan ON t_rag_plan_question_ref (plan_id);

-- 3. 实验计划自定义提问
CREATE TABLE IF NOT EXISTS t_rag_plan_custom_question (
    id BIGSERIAL PRIMARY KEY,
    plan_id BIGINT NOT NULL,
    question_text TEXT NOT NULL,
    expected_answer TEXT,
    expected_doc_ids TEXT,                -- 预期文档 ID（JSON）
    create_user_id BIGINT,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_plan_custom_q_plan ON t_rag_plan_custom_question (plan_id);

-- 4. 生产数据回流申请
CREATE TABLE IF NOT EXISTS t_data_replay_request (
    id BIGSERIAL PRIMARY KEY,
    trace_id VARCHAR(64) NOT NULL,        -- 来源链路
    question_text TEXT NOT NULL,           -- 回捞的提问
    source_conversation_id VARCHAR(64),    -- 来源会话
    status VARCHAR(20) DEFAULT 'PENDING',  -- PENDING / APPROVED / REJECTED
    approved_by BIGINT,
    approved_time TIMESTAMP,
    target_question_id BIGINT,             -- 审批通过后写入 t_rag_test_question 的 ID
    create_user_id BIGINT,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_data_replay_status ON t_data_replay_request (status);
