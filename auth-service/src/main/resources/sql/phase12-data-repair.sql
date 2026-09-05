-- Phase 12: 数据修复
-- 背景：
-- 1) auth-service 此前未做 Long→String 序列化，雪花 id 在前端 JS Number 精度丢失，
--    导致角色分配写到了错误 user_id、团队成员/子部门关联失败
-- 2) 早期版本创建对话未写 user_id/tenant_id，用户隔离上线后这些对话无人可见
-- 3) t_team.parent_id=0 应为 NULL（根节点），「商业部门1子部门1」应挂到「商业部门1」下

-- 1. 清理因精度丢失写入的错误用户-角色行（user1 真实 id 为 2095490705978834946）
DELETE FROM t_user_role WHERE user_id = 2095490705978835000;

-- 2. 存量无角色用户批量补 USER（普通用户）角色（幂等）
INSERT INTO t_user_role (id, user_id, role_id)
SELECT 120000 + row_number() OVER (), u.id, 2
FROM t_user u
WHERE u.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM t_user_role ur WHERE ur.user_id = u.id)
ON CONFLICT DO NOTHING;

-- 3. 团队层级修复：parent_id=0 视为根节点
UPDATE t_team SET parent_id = NULL WHERE parent_id = 0;

-- 4. 「商业部门1子部门1」挂回「商业部门1」（已与需求方确认）
UPDATE t_team SET parent_id = 2095494808658264065 WHERE id = 2095494854392954882;

-- 5. 历史无归属对话归到 admin（id=1）/ 默认租户（id=1）（已与需求方确认）
UPDATE t_conversation SET user_id = 1, tenant_id = 1 WHERE user_id IS NULL;
