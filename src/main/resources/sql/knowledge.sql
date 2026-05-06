-- 知识库表
CREATE TABLE IF NOT EXISTS t_knowledge_base
(
    id          BIGINT PRIMARY KEY,
    name        VARCHAR(128) NOT NULL,
    description VARCHAR(512),
    created_by  VARCHAR(64),
    updated_by  VARCHAR(64),
    create_time TIMESTAMP    NOT NULL DEFAULT NOW(),
    update_time TIMESTAMP    NOT NULL DEFAULT NOW(),
    deleted     SMALLINT     NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_knowledge_base_name ON t_knowledge_base (name) WHERE deleted = 0;

-- 知识库文档表
CREATE TABLE IF NOT EXISTS t_knowledge_document
(
    id          BIGINT PRIMARY KEY,
    kb_id       BIGINT       NOT NULL,
    doc_name    VARCHAR(256) NOT NULL,
    file_url    VARCHAR(1024),
    file_type   VARCHAR(64),
    file_size   BIGINT,
    source_type VARCHAR(32)  NOT NULL DEFAULT 'file',
    status      VARCHAR(32)  NOT NULL DEFAULT 'pending',
    enabled     SMALLINT     NOT NULL DEFAULT 1,
    created_by  VARCHAR(64),
    updated_by  VARCHAR(64),
    create_time TIMESTAMP    NOT NULL DEFAULT NOW(),
    update_time TIMESTAMP    NOT NULL DEFAULT NOW(),
    deleted     SMALLINT     NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_knowledge_document_kb_id ON t_knowledge_document (kb_id);

-- 文件内容存储表（替代 MinIO，用 Supabase PostgreSQL 的 bytea 存储原始文件）
CREATE TABLE IF NOT EXISTS t_knowledge_document_file
(
    id           BIGSERIAL PRIMARY KEY,
    storage_key  VARCHAR(512) NOT NULL UNIQUE,
    content      BYTEA        NOT NULL,
    content_type VARCHAR(128),
    create_time  TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_doc_file_storage_key ON t_knowledge_document_file (storage_key);