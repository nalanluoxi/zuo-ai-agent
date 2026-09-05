-- Phase 13: 角色权限统一配置化 + 意图闲聊节点补齐
-- 1. SUPER_ADMIN 页面权限落表（与普通角色一样通过 t_role_permission 配置，代码不再硬编码放行）
-- 2. 为所有缺少「闲聊」系统意图节点的租户补齐

-- SUPER_ADMIN（role_id=1）默认拥有全部页面 ADMIN 权限
INSERT INTO t_role_permission (id, role_id, permission_id, access_level)
SELECT 1100 + p.id, 1, p.id, 'ADMIN'
FROM t_permission p
WHERE p.resource_type = 'PAGE'
  AND NOT EXISTS (SELECT 1 FROM t_role_permission rp WHERE rp.role_id = 1 AND rp.permission_id = p.id);

-- 为缺少「闲聊」系统节点的租户补齐（幂等）
INSERT INTO t_intent_node (id, tenant_id, parent_id, label, description, level, is_system, sort_order, enabled, deleted, create_time, update_time)
SELECT (CAST(EXTRACT(EPOCH FROM NOW()) AS BIGINT) * 1000 + ROW_NUMBER() OVER ()) AS id,
       t.id, NULL, '闲聊', '默认闲聊节点，处理非业务问题、问候、闲聊等', 1, 1, 0, 1, 0, NOW(), NOW()
FROM t_tenant t
WHERE t.deleted = 0
  AND NOT EXISTS (
      SELECT 1 FROM t_intent_node n
      WHERE n.tenant_id = t.id AND n.is_system = 1 AND n.label = '闲聊' AND n.deleted = 0
  );
