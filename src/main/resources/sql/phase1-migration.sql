-- Phase 1: knowledge document 表新增 content_md5 字段
ALTER TABLE t_knowledge_document ADD COLUMN IF NOT EXISTS content_md5 VARCHAR(32);
CREATE UNIQUE INDEX IF NOT EXISTS uk_kb_content_md5 ON t_knowledge_document (kb_id, content_md5) WHERE deleted = 0;

-- Phase 1: 对话记忆新表 — 原始消息记录表
CREATE TABLE IF NOT EXISTS t_chat_message_raw (
    id BIGINT PRIMARY KEY,
    msg_id VARCHAR(64) NOT NULL,
    conversation_id VARCHAR(64) NOT NULL,
    role VARCHAR(16) NOT NULL,
    content TEXT NOT NULL,
    create_time TIMESTAMP DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_raw_conversation ON t_chat_message_raw (conversation_id, create_time);
CREATE UNIQUE INDEX IF NOT EXISTS uk_raw_msg_id ON t_chat_message_raw (msg_id);

-- Phase 1: 对话记忆新表 — 压缩记录表
CREATE TABLE IF NOT EXISTS t_chat_message_compression (
    id BIGINT PRIMARY KEY,
    conversation_id VARCHAR(64) NOT NULL,
    summary_content TEXT NOT NULL,
    source_msg_ids TEXT,
    source_count INT DEFAULT 0,
    create_time TIMESTAMP DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_compression_conversation ON t_chat_message_compression (conversation_id, create_time);