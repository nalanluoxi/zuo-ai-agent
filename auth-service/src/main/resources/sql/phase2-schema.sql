-- Phase 2: auth-service 表结构
CREATE TABLE IF NOT EXISTS t_tenant (
    id BIGINT PRIMARY KEY, tenant_name VARCHAR(128), tenant_code VARCHAR(64) UNIQUE,
    status SMALLINT DEFAULT 1, create_time TIMESTAMP, update_time TIMESTAMP, deleted SMALLINT DEFAULT 0
);
CREATE TABLE IF NOT EXISTS t_user (
    id BIGINT PRIMARY KEY, username VARCHAR(64) UNIQUE, password VARCHAR(128), nickname VARCHAR(64),
    email VARCHAR(128), tenant_id BIGINT, status SMALLINT DEFAULT 1,
    create_time TIMESTAMP, update_time TIMESTAMP, deleted SMALLINT DEFAULT 0
);
CREATE TABLE IF NOT EXISTS t_role (
    id BIGINT PRIMARY KEY, role_code VARCHAR(64) UNIQUE, role_name VARCHAR(128),
    scope_type VARCHAR(16), data_scope VARCHAR(16), tenant_id BIGINT,
    create_time TIMESTAMP, update_time TIMESTAMP, deleted SMALLINT DEFAULT 0
);
CREATE TABLE IF NOT EXISTS t_permission (
    id BIGINT PRIMARY KEY, perm_code VARCHAR(128) UNIQUE, perm_name VARCHAR(128), resource_type VARCHAR(32),
    create_time TIMESTAMP, update_time TIMESTAMP, deleted SMALLINT DEFAULT 0
);
CREATE TABLE IF NOT EXISTS t_user_role (
    id BIGINT PRIMARY KEY, user_id BIGINT, role_id BIGINT, UNIQUE(user_id, role_id)
);
CREATE TABLE IF NOT EXISTS t_role_permission (
    id BIGINT PRIMARY KEY, role_id BIGINT, permission_id BIGINT, UNIQUE(role_id, permission_id)
);
CREATE TABLE IF NOT EXISTS t_team (
    id BIGINT PRIMARY KEY, team_name VARCHAR(128), tenant_id BIGINT, owner_id BIGINT,
    status SMALLINT DEFAULT 1, create_time TIMESTAMP, update_time TIMESTAMP, deleted SMALLINT DEFAULT 0
);
CREATE TABLE IF NOT EXISTS t_user_team (
    id BIGINT PRIMARY KEY, user_id BIGINT, team_id BIGINT, role_in_team VARCHAR(32), UNIQUE(user_id, team_id)
);
CREATE TABLE IF NOT EXISTS t_approval (
    id BIGINT PRIMARY KEY, apply_type VARCHAR(32), applicant_id BIGINT,
    target_type VARCHAR(32), target_id BIGINT, target_name VARCHAR(256),
    approver_type VARCHAR(32), approver_id BIGINT, status VARCHAR(16) DEFAULT 'PENDING',
    reject_reason VARCHAR(512), apply_time TIMESTAMP, approve_time TIMESTAMP
);
CREATE TABLE IF NOT EXISTS t_audit_log (
    id BIGINT PRIMARY KEY, user_id BIGINT, action VARCHAR(32),
    target_type VARCHAR(32), target_id BIGINT, detail TEXT,
    ip_address VARCHAR(64), user_agent VARCHAR(256), create_time TIMESTAMP DEFAULT NOW()
);
CREATE TABLE IF NOT EXISTS t_resource_access (
    id BIGINT PRIMARY KEY, resource_type VARCHAR(32), resource_id BIGINT,
    grantee_type VARCHAR(16), grantee_id BIGINT, permission VARCHAR(16),
    granted_by BIGINT, create_time TIMESTAMP DEFAULT NOW()
);

-- 种子数据
INSERT INTO t_tenant (id, tenant_name, tenant_code, status) VALUES (1, '默认租户', 'default', 1) ON CONFLICT DO NOTHING;
INSERT INTO t_role (id, role_code, role_name, scope_type, data_scope) VALUES (1, 'SUPER_ADMIN', '超级管理员', 'TENANT', 'GLOBAL') ON CONFLICT DO NOTHING;
INSERT INTO t_permission (id, perm_code, perm_name, resource_type) VALUES
    (1, 'kb:list', '知识库列表', 'API'),
    (2, 'kb:create', '创建知识库', 'API'),
    (3, 'kb:view', '查看知识库', 'API'),
    (4, 'kb:delete', '删除知识库', 'API'),
    (5, 'doc:upload', '上传文档', 'API'),
    (6, 'chat:use', '使用对话', 'API'),
    (7, 'dashboard:view', '查看看板', 'API'),
    (8, 'rbac:user', '管理用户', 'API'),
    (9, 'rbac:role', '管理角色', 'API'),
    (10, 'team:manage', '管理团队', 'API'),
    (11, 'apply:view', '查看审批', 'API')
ON CONFLICT DO NOTHING;

-- t_knowledge_base 增加 team_id, owner_id
ALTER TABLE t_knowledge_base ADD COLUMN IF NOT EXISTS team_id BIGINT;
ALTER TABLE t_knowledge_base ADD COLUMN IF NOT EXISTS owner_id BIGINT;