-- Phase 11: 可配置页面级权限
-- 1. t_role_permission 增加访问级别（READ 只读 / WRITE 修改 / ADMIN 超级管理）
-- 2. t_permission 播种页面清单（resource_type='PAGE'）
-- 3. 播种普通用户角色 USER 及其默认页面权限（只读）

ALTER TABLE t_role_permission ADD COLUMN IF NOT EXISTS access_level VARCHAR(16) DEFAULT 'READ';

-- 页面清单（page_code 即前端路由 meta.page 与后端拦截映射使用的编码）
INSERT INTO t_permission (id, perm_code, perm_name, resource_type) VALUES
    (101, 'chat', 'RAG对话', 'PAGE'),
    (102, 'knowledge', '知识库', 'PAGE'),
    (103, 'intent', '意图管理', 'PAGE'),
    (104, 'dashboard', '个人看板', 'PAGE'),
    (105, 'monitor:redis', 'Redis监控', 'PAGE'),
    (106, 'monitor:db', 'DB监控', 'PAGE'),
    (107, 'monitor:logs', '日志系统', 'PAGE'),
    (108, 'manage:dashboard', '全局看板', 'PAGE'),
    (109, 'manage:trace', '链路详情', 'PAGE'),
    (110, 'manage:tenants', '租户管理', 'PAGE'),
    (111, 'manage:approvals', '审批中心', 'PAGE')
ON CONFLICT DO NOTHING;

-- 普通用户角色（新注册用户默认分配）
INSERT INTO t_role (id, role_code, role_name, scope_type, data_scope)
VALUES (2, 'USER', '普通用户', 'TENANT', 'SELF')
ON CONFLICT DO NOTHING;

-- USER 默认页面权限：RAG对话/知识库/意图管理/个人看板/审批中心/租户管理 只读
INSERT INTO t_role_permission (id, role_id, permission_id, access_level) VALUES
    (1001, 2, 101, 'READ'),
    (1002, 2, 102, 'READ'),
    (1003, 2, 103, 'READ'),
    (1004, 2, 104, 'READ'),
    (1005, 2, 111, 'READ'),
    (1006, 2, 110, 'READ')
ON CONFLICT DO NOTHING;
