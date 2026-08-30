-- 意图节点表（三级层次：domain → category → topic）
CREATE TABLE IF NOT EXISTS t_intent_node
(
    id          BIGINT PRIMARY KEY,
    tenant_id   BIGINT,                          -- 租户 ID（多租户隔离）
    parent_id   BIGINT,                          -- NULL 表示顶级节点
    label       VARCHAR(64)  NOT NULL,           -- 节点名称，如"金融"、"股票投资"
    description VARCHAR(256),                    -- 节点描述，用于 LLM 分类 Prompt
    level       SMALLINT     NOT NULL DEFAULT 1, -- 1=domain, 2=category, 3=topic
    is_system   SMALLINT     NOT NULL DEFAULT 0, -- 1=系统/闲聊节点，直接短路
    kb_id       BIGINT,                          -- 关联知识库（topic 级别才填）
    sort_order  INT          NOT NULL DEFAULT 0,
    create_time TIMESTAMP    NOT NULL DEFAULT NOW(),
    update_time TIMESTAMP    NOT NULL DEFAULT NOW(),
    deleted     SMALLINT     NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_intent_node_parent_id ON t_intent_node (parent_id);
CREATE INDEX IF NOT EXISTS idx_intent_node_level ON t_intent_node (level);

-- 初始化数据：系统/闲聊节点
INSERT INTO t_intent_node (id, parent_id, label, description, level, is_system, kb_id, sort_order)
VALUES (1, NULL, '闲聊/通用', '用户问候、闲聊、系统类问题，如"你好"、"你是谁"、"今天天气怎么样"', 1, 1, NULL, 0)
ON CONFLICT (id) DO NOTHING;

-- 初始化数据：金融领域（一级）
INSERT INTO t_intent_node (id, parent_id, label, description, level, is_system, kb_id, sort_order)
VALUES (2, NULL, '金融', '金融工程、金融产品、金融市场相关问题', 1, 0, NULL, 1)
ON CONFLICT (id) DO NOTHING;

-- 初始化数据：金融工程（二级）
INSERT INTO t_intent_node (id, parent_id, label, description, level, is_system, kb_id, sort_order)
VALUES (3, 2, '金融工程', '金融工程技术、衍生品定价、风险管理相关问题', 2, 0, NULL, 0)
ON CONFLICT (id) DO NOTHING;

-- 初始化数据：投资估价（二级）
INSERT INTO t_intent_node (id, parent_id, label, description, level, is_system, kb_id, sort_order)
VALUES (4, 2, '投资估价', '股票估值、市盈率、投资分析相关问题', 2, 0, NULL, 1)
ON CONFLICT (id) DO NOTHING;

-- 初始化数据：宪法学领域（一级）
INSERT INTO t_intent_node (id, parent_id, label, description, level, is_system, kb_id, sort_order)
VALUES (5, NULL, '宪法学', '宪法条文、公民权利、国家机构相关问题', 1, 0, NULL, 2)
ON CONFLICT (id) DO NOTHING;

-- 初始化数据：宪法学（二级，指向知识库）
-- 注意：kb_id 需根据实际知识库 ID 更新
INSERT INTO t_intent_node (id, parent_id, label, description, level, is_system, kb_id, sort_order)
VALUES (6, 5, '宪法基础', '宪法基本原则、宪法历史、公民基本权利与义务', 2, 0, NULL, 0)
ON CONFLICT (id) DO NOTHING;
