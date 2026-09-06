-- ============================================================
-- Phase 3: LLM 模型配置管理
-- 创建时间: 2026-09-06
-- 作者: 浩浩
-- ============================================================

-- 1. LLM 模型配置表
CREATE TABLE IF NOT EXISTS t_llm_model_config (
    id BIGSERIAL PRIMARY KEY,
    model_name VARCHAR(100) NOT NULL,       -- 显示名称（如"百炼 qwen-turbo"）
    provider VARCHAR(50) NOT NULL,          -- bailian/deepseek/siliconflow/openai/agnes
    base_url VARCHAR(500) NOT NULL,         -- API 地址
    api_key TEXT NOT NULL,                  -- Jasypt 加密存储
    model_id VARCHAR(100) NOT NULL,         -- 平台模型标识
    max_tokens INT DEFAULT 4096,
    temperature DOUBLE PRECISION DEFAULT 0.7,
    is_active SMALLINT DEFAULT 1,           -- 1=对话模块下拉可见
    status VARCHAR(20) DEFAULT 'DRAFT',     -- 支持灰度发布
    create_user_id BIGINT,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 2. 插入默认模型配置（从现有硬编码迁移）
INSERT INTO t_llm_model_config (model_name, provider, base_url, api_key, model_id, max_tokens, temperature, is_active, status)
VALUES
    ('百炼 qwen-turbo', 'bailian', 'https://dashscope.aliyuncs.com/compatible-mode/v1',
     'sk-placeholder-bailian', 'qwen-turbo', 4096, 0.7, 1, 'DRAFT'),
    ('DeepSeek Chat', 'deepseek', 'https://api.deepseek.com/v1',
     'sk-placeholder-deepseek', 'deepseek-chat', 4096, 0.7, 1, 'DRAFT'),
    ('硅基流动 Qwen2.5-72B', 'siliconflow', 'https://api.siliconflow.cn/v1',
     'sk-placeholder-siliconflow', 'Qwen/Qwen2.5-72B-Instruct', 4096, 0.7, 1, 'DRAFT'),
    ('Agnes AI Flash', 'agnes', 'https://api.agnes-ai.cn/v1',
     'sk-placeholder-agnes', 'agnes-2.5-flash', 4096, 0.7, 1, 'DRAFT');
