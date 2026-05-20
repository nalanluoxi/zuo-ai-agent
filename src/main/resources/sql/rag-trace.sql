-- RAG 链路追踪表
-- 每次流水线调用产生一条 run 记录 + 若干 node 记录

-- 流水线级别追踪（一次完整调用 = 一条记录）
CREATE TABLE IF NOT EXISTS t_rag_trace_run
(
    id               BIGSERIAL    PRIMARY KEY,
    trace_id         VARCHAR(64)  NOT NULL UNIQUE,   -- 全局链路ID（UUID）
    conversation_id  VARCHAR(64),                    -- 会话ID
    original_prompt  TEXT,                           -- 用户原始问题
    status           VARCHAR(16)  NOT NULL DEFAULT 'RUNNING', -- RUNNING / SUCCESS / ERROR
    error_message    TEXT,                           -- 错误信息
    start_time       TIMESTAMP    NOT NULL DEFAULT NOW(),
    end_time         TIMESTAMP,
    duration_ms      BIGINT,                         -- 总耗时（毫秒）
    create_time      TIMESTAMP    NOT NULL DEFAULT NOW(),
    update_time      TIMESTAMP    NOT NULL DEFAULT NOW(),
    deleted          SMALLINT     NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_rag_trace_run_trace_id       ON t_rag_trace_run (trace_id);
CREATE INDEX IF NOT EXISTS idx_rag_trace_run_conversation_id ON t_rag_trace_run (conversation_id);
CREATE INDEX IF NOT EXISTS idx_rag_trace_run_create_time    ON t_rag_trace_run (create_time DESC);

-- 节点级别追踪（每个流水线阶段 = 一条记录）
CREATE TABLE IF NOT EXISTS t_rag_trace_node
(
    id             BIGSERIAL    PRIMARY KEY,
    trace_id       VARCHAR(64)  NOT NULL,            -- 关联 t_rag_trace_run.trace_id
    node_id        VARCHAR(64)  NOT NULL,            -- 节点唯一ID（如 "rewrite"、"classify"）
    node_name      VARCHAR(128),                     -- 节点展示名称
    node_type      VARCHAR(32),                      -- 节点类型：REWRITE/CLASSIFY/RETRIEVE/RERANK/PROMPT/LLM
    status         VARCHAR(16)  NOT NULL DEFAULT 'RUNNING',
    error_message  TEXT,
    start_time     TIMESTAMP    NOT NULL DEFAULT NOW(),
    end_time       TIMESTAMP,
    duration_ms    BIGINT,                           -- 该节点耗时（毫秒）
    input_data     TEXT,                             -- 节点输入（JSON字符串）
    output_data    TEXT,                             -- 节点输出（JSON字符串，含检索文档内容）
    create_time    TIMESTAMP    NOT NULL DEFAULT NOW(),
    update_time    TIMESTAMP    NOT NULL DEFAULT NOW(),
    deleted        SMALLINT     NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_rag_trace_node_trace_id ON t_rag_trace_node (trace_id);
CREATE INDEX IF NOT EXISTS idx_rag_trace_node_node_id  ON t_rag_trace_node (trace_id, node_id);
