-- Phase 13: 修复 vector_store 表结构，添加 doc_id 列用于关联知识库文档
-- 创建时间: 2026-09-08
-- 说明: vector_store 表需要 doc_id 列来关联知识库文档，支持按文档删除向量数据

-- 添加 doc_id 列
ALTER TABLE vector_store ADD COLUMN IF NOT EXISTS doc_id VARCHAR(64);

-- 创建索引以加速按文档 ID 查询
CREATE INDEX IF NOT EXISTS idx_vector_store_doc_id ON vector_store (doc_id);

-- 添加注释
COMMENT ON COLUMN vector_store.doc_id IS '关联的知识库文档 ID，来自 t_knowledge_document.id';
