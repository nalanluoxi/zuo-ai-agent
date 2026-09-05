-- Phase 14: 租户模型切换——存量用户迁入默认租户
-- 背景：注册模型从"每人一个租户"改为"加入默认组织"，存量用户统一归属默认租户(1)
-- 已验证 test/testuser/user1 的个人租户下无任何自建数据（知识库/意图节点/对话/部门）

UPDATE t_user SET tenant_id = 1, update_time = NOW()
WHERE deleted = 0 AND tenant_id <> 1;
