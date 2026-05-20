-- 对话记忆持久化表
-- 存储每轮对话的消息（role: user / assistant / system）
CREATE TABLE IF NOT EXISTS t_chat_memory
(
    id              BIGSERIAL    PRIMARY KEY,
    conversation_id VARCHAR(64)  NOT NULL,        -- 会话 ID（雪花 ID 字符串）
    role            VARCHAR(16)  NOT NULL,         -- user / assistant / system
    content         TEXT         NOT NULL,         -- 消息内容（含摘要时以 [对话摘要] 开头）
    create_time     TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- 按会话 ID 查询的主索引
CREATE INDEX IF NOT EXISTS idx_chat_memory_conversation ON t_chat_memory (conversation_id, id ASC);