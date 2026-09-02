-- Phase 7: ETL 修复 + 入库打点日志表
-- 修复 ETL 链路阻塞问题 + 添加入库流程打点记录

-- 1. t_knowledge_document 加 content_md5 列（ETL persist() 需要）
ALTER TABLE t_knowledge_document
    ADD COLUMN IF NOT EXISTS content_md5 VARCHAR(64);

CREATE INDEX IF NOT EXISTS idx_knowledge_document_md5 ON t_knowledge_document (kb_id, content_md5) WHERE deleted = 0;

-- 2. t_knowledge_document 加 search_vector 列（全文检索需要）
ALTER TABLE t_knowledge_document
    ADD COLUMN IF NOT EXISTS search_vector tsvector;

-- 创建 GIN 索引加速全文检索
CREATE INDEX IF NOT EXISTS idx_knowledge_document_search_vector ON t_knowledge_document USING GIN (search_vector);

-- 创建触发器：doc_name 变更时自动更新 search_vector
CREATE OR REPLACE FUNCTION update_knowledge_document_search_vector()
RETURNS TRIGGER AS $$
BEGIN
    NEW.search_vector := to_tsvector('simple', COALESCE(NEW.doc_name, ''));
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_knowledge_document_search_vector
    BEFORE INSERT OR UPDATE OF doc_name ON t_knowledge_document
    FOR EACH ROW
    EXECUTE FUNCTION update_knowledge_document_search_vector();

-- 回填已有数据的 search_vector（如果表里有数据）
UPDATE t_knowledge_document SET search_vector = to_tsvector('simple', COALESCE(doc_name, '')) WHERE search_vector IS NULL;

-- 3. 创建入库日志表 t_ingestion_log（ETL 打点记录）
CREATE TABLE IF NOT EXISTS t_ingestion_log (
    id              BIGSERIAL PRIMARY KEY,
    doc_id          BIGINT NOT NULL,
    kb_id           BIGINT NOT NULL,
    stage           VARCHAR(32) NOT NULL,     -- upload / parse / chunk / vectorize / complete / failed
    status          VARCHAR(16) NOT NULL,     -- success / failed
    duration_ms     INTEGER,                  -- 本阶段耗时（毫秒）
    total_duration_ms INTEGER,                -- 从上传到当前阶段的累计耗时
    chunks_count    INTEGER DEFAULT 0,        -- 分块数（chunk 阶段记录）
    chunks_success  INTEGER DEFAULT 0,        -- 成功写入向量库的块数（vectorize 阶段记录）
    error_message   TEXT,                     -- 失败时的错误信息
    file_type       VARCHAR(64),              -- 文件类型
    file_size       BIGINT,                   -- 文件大小（字节）
    text_length     INTEGER,                  -- 解析后文本长度（字符）
    strategy        VARCHAR(32),              -- 分块策略（FIXED_SIZE / STRUCTURE_AWARE）
    created_at      TIMESTAMP DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_ingestion_log_doc ON t_ingestion_log (doc_id);
CREATE INDEX IF NOT EXISTS idx_ingestion_log_kb ON t_ingestion_log (kb_id);
CREATE INDEX IF NOT EXISTS idx_ingestion_log_stage ON t_ingestion_log (stage, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_ingestion_log_date ON t_ingestion_log (DATE(created_at));
CREATE INDEX IF NOT EXISTS idx_ingestion_log_status ON t_ingestion_log (status, created_at DESC);

-- 4. 创建入库统计视图（供 Dashboard 查询）
CREATE OR REPLACE VIEW v_ingestion_daily_stats AS
SELECT
    DATE(created_at) as stat_date,
    kb_id,
    COUNT(*) as total_count,
    COUNT(CASE WHEN status = 'success' THEN 1 END) as success_count,
    COUNT(CASE WHEN status = 'failed' THEN 1 END) as failed_count,
    ROUND(AVG(CASE WHEN total_duration_ms IS NOT NULL THEN total_duration_ms END)::numeric, 0) as avg_duration_ms,
    ROUND(MAX(CASE WHEN total_duration_ms IS NOT NULL THEN total_duration_ms END)::numeric, 0) as max_duration_ms,
    ROUND(SUM(CASE WHEN stage = 'complete' THEN chunks_success ELSE 0 END)::numeric, 0) as total_chunks,
    ROUND(AVG(CASE WHEN stage = 'chunk' THEN chunks_count ELSE NULL END)::numeric, 0) as avg_chunks_per_doc
FROM t_ingestion_log
GROUP BY DATE(created_at), kb_id;
