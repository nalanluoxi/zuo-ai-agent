-- 修复意图节点 tenant_id 为 null 的历史数据
-- 将所有 tenant_id 为 null 的记录更新为默认租户 ID (1)
-- 必须在应用启动前执行，代码层保持严格校验

UPDATE t_intent_node
SET tenant_id = 1
WHERE tenant_id IS NULL AND deleted = 0;

-- 验证修复结果
SELECT id, label, tenant_id, deleted
FROM t_intent_node
WHERE deleted = 0
ORDER BY id;
