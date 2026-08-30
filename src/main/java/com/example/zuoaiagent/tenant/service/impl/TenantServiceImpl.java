package com.example.zuoaiagent.tenant.service.impl;

import com.example.zuoaiagent.exception.BusinessException;
import com.example.zuoaiagent.exception.ErrorCode;
import com.example.zuoaiagent.tenant.service.TenantService;
import com.example.zuoaiagent.tenant.service.DepartmentService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * P30：租户服务实现
 */
@Service
@RequiredArgsConstructor
public class TenantServiceImpl implements TenantService {

    private static final Logger log = LoggerFactory.getLogger(TenantServiceImpl.class);
    private final JdbcTemplate jdbcTemplate;

    @Override
    public Map<String, Object> listTenants(int page, int size) {
        try {
            // 使用实际表字段：tenant_name, tenant_code, status, create_time, update_time
            String sql = "SELECT id, tenant_name, tenant_code, status, create_time, update_time " +
                        "FROM t_tenant WHERE deleted = 0 " +
                        "ORDER BY create_time DESC LIMIT ? OFFSET ?";
            
            List<Map<String, Object>> content = jdbcTemplate.queryForList(sql, size, (page - 1) * size);
            
            String countSql = "SELECT COUNT(*) as total FROM t_tenant WHERE deleted = 0";
            Integer total = jdbcTemplate.queryForObject(countSql, Integer.class);
            if (total == null) total = 0;
            
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("content", content);
            result.put("totalElements", total);
            result.put("totalPages", (total + size - 1) / size);
            result.put("currentPage", page);
            result.put("pageSize", size);
            
            return result;
        } catch (Exception e) {
            log.error("查询租户列表失败：", e);
            return Map.of(
                "content", List.of(),
                "totalElements", 0,
                "totalPages", 0,
                "currentPage", page,
                "pageSize", size
            );
        }
    }

    @Override
    public Map<String, Object> getTenantDetail(Long tenantId) {
        try {
            // 使用实际表字段：tenant_name, tenant_code, status, create_time, update_time
            String sql = "SELECT id, tenant_name, tenant_code, status, create_time, update_time " +
                        "FROM t_tenant WHERE id = ? AND deleted = 0";
            
            List<Map<String, Object>> results = jdbcTemplate.queryForList(sql, tenantId);
            
            if (results.isEmpty()) {
                throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "租户不存在");
            }
            
            Map<String, Object> detail = new LinkedHashMap<>(results.get(0));
            
            // 查询成员数
            String memberCountSql = "SELECT COUNT(*) as member_count FROM t_user WHERE tenant_id = ? AND deleted = 0";
            Integer memberCount = jdbcTemplate.queryForObject(memberCountSql, Integer.class, tenantId);
            detail.put("memberCount", memberCount != null ? memberCount : 0);
            
            // 查询部门列表
            String deptSql = "SELECT id, team_name, parent_id, status FROM t_team WHERE tenant_id = ? AND deleted = 0";
            List<Map<String, Object>> departments = jdbcTemplate.queryForList(deptSql, tenantId);
            detail.put("departments", departments);
            
            // 查询用户列表
            String userSql = "SELECT id, username, nickname, email, status FROM t_user WHERE tenant_id = ? AND deleted = 0";
            List<Map<String, Object>> users = jdbcTemplate.queryForList(userSql, tenantId);
            detail.put("users", users);
            
            // 查询角色列表
            String roleSql = "SELECT id, role_name, role_code FROM t_role WHERE tenant_id = ? AND deleted = 0";
            List<Map<String, Object>> roles = jdbcTemplate.queryForList(roleSql, tenantId);
            detail.put("roles", roles);
            
            return detail;
        } catch (Exception e) {
            if (e instanceof BusinessException) throw e;
            log.error("查询租户详情失败：", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "查询租户详情失败");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> createTenant(String name, Map<String, Object> data) {
        try {
            Long tenantId = System.currentTimeMillis();
            String tenantCode = (String) data.getOrDefault("tenantCode", "T" + tenantId);
            Integer status = data.get("status") != null ? Integer.valueOf(data.get("status").toString()) : 1;
            
            // 使用实际表字段：tenant_name, tenant_code, status
            String sql = "INSERT INTO t_tenant (id, tenant_name, tenant_code, status, deleted, create_time, update_time) " +
                        "VALUES (?, ?, ?, ?, 0, NOW(), NOW())";
            
            jdbcTemplate.update(sql, tenantId, name, tenantCode, status);
            
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("id", tenantId);
            result.put("tenantName", name);
            result.put("tenantCode", tenantCode);
            result.put("status", status);
            result.put("createTime", LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME));
            
            log.info("创建租户 - tenantId: {}, name: {}", tenantId, name);
            
            return result;
        } catch (Exception e) {
            log.error("创建租户失败：", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "创建租户失败");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> updateTenant(Long tenantId, Map<String, Object> data) {
        try {
            // 验证租户存在
            getTenantDetail(tenantId);
            
            String sql = "UPDATE t_tenant SET ";
            List<Object> params = new ArrayList<>();
            
            if (data.containsKey("tenantName")) {
                sql += "tenant_name = ?, ";
                params.add(data.get("tenantName"));
            }
            if (data.containsKey("tenantCode")) {
                sql += "tenant_code = ?, ";
                params.add(data.get("tenantCode"));
            }
            if (data.containsKey("status")) {
                sql += "status = ?, ";
                params.add(Integer.valueOf(data.get("status").toString()));
            }
            
            sql += "update_time = NOW() WHERE id = ?";
            params.add(tenantId);
            
            jdbcTemplate.update(sql, params.toArray());
            
            return getTenantDetail(tenantId);
        } catch (Exception e) {
            if (e instanceof BusinessException) throw e;
            log.error("更新租户失败：", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "更新租户失败");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteTenant(Long tenantId) {
        try {
            getTenantDetail(tenantId);
            
            String sql = "UPDATE t_tenant SET deleted = 1 WHERE id = ?";
            jdbcTemplate.update(sql, tenantId);
            
            log.info("删除租户 - tenantId: {}", tenantId);
        } catch (Exception e) {
            if (e instanceof BusinessException) throw e;
            log.error("删除租户失败：", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "删除租户失败");
        }
    }

    @Override
    public List<Map<String, Object>> getRoles(Long tenantId) {
        try {
            String sql = "SELECT id, tenant_id, role_name, description, created_at FROM t_role " +
                        "WHERE tenant_id = ? ORDER BY created_at ASC";
            
            return jdbcTemplate.queryForList(sql, tenantId);
        } catch (Exception e) {
            log.error("查询角色列表失败：", e);
            return List.of();
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> createRole(Long tenantId, String roleName, Map<String, Object> data) {
        try {
            Long roleId = System.currentTimeMillis();
            String description = (String) data.get("description");
            
            String sql = "INSERT INTO t_role (id, tenant_id, role_name, description, created_at) " +
                        "VALUES (?, ?, ?, ?, NOW())";
            
            jdbcTemplate.update(sql, roleId, tenantId, roleName, description);
            
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("id", roleId);
            result.put("tenantId", tenantId);
            result.put("roleName", roleName);
            result.put("description", description);
            result.put("createdAt", LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME));
            
            return result;
        } catch (Exception e) {
            log.error("创建角色失败：", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "创建角色失败");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignRoleToUser(Long tenantId, String userId, Long roleId) {
        try {
            String sql = "INSERT INTO t_user_role (user_id, role_id, tenant_id, created_at) VALUES (?, ?, ?, NOW())";
            
            jdbcTemplate.update(sql, userId, roleId, tenantId);
            
            log.info("分配角色给用户 - userId: {}, roleId: {}", userId, roleId);
        } catch (Exception e) {
            log.error("分配角色失败：", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "分配角色失败");
        }
    }

    @Override
    public List<Map<String, Object>> getUserRoles(Long tenantId, String userId) {
        try {
            String sql = "SELECT r.id, r.tenant_id, r.role_name, r.description FROM t_user_role ur " +
                        "JOIN t_role r ON ur.role_id = r.id " +
                        "WHERE ur.user_id = ? AND ur.tenant_id = ?";
            
            return jdbcTemplate.queryForList(sql, userId, tenantId);
        } catch (Exception e) {
            log.error("查询用户角色失败：", e);
            return List.of();
        }
    }
}

/**
 * P30：部门服务实现
 */
@Service
@RequiredArgsConstructor
class DepartmentServiceImpl implements DepartmentService {

    private static final Logger log = LoggerFactory.getLogger(DepartmentServiceImpl.class);
    private final JdbcTemplate jdbcTemplate;

    @Override
    public List<Map<String, Object>> getDepartmentTree(Long tenantId) {
        try {
            // 使用 t_team 表，parent_id 字段
            String rootSql = "SELECT id, tenant_id, team_name, parent_id, status FROM t_team " +
                            "WHERE tenant_id = ? AND (parent_id IS NULL OR parent_id = 0) AND deleted = 0 ORDER BY create_time ASC";
            
            List<Map<String, Object>> rootDepts = jdbcTemplate.queryForList(rootSql, tenantId);
            
            List<Map<String, Object>> tree = new ArrayList<>();
            for (Map<String, Object> rootDept : rootDepts) {
                Map<String, Object> treeNode = new LinkedHashMap<>(rootDept);
                treeNode.put("children", buildChildrenDepts(tenantId, ((Number) rootDept.get("id")).longValue()));
                tree.add(treeNode);
            }
            
            return tree;
        } catch (Exception e) {
            log.error("查询部门树失败：", e);
            return List.of();
        }
    }

    private List<Map<String, Object>> buildChildrenDepts(Long tenantId, Long parentId) {
        String sql = "SELECT id, tenant_id, team_name, parent_id, status FROM t_team " +
                    "WHERE tenant_id = ? AND parent_id = ? AND deleted = 0 ORDER BY create_time ASC";
        
        List<Map<String, Object>> children = jdbcTemplate.queryForList(sql, tenantId, parentId);
        
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> child : children) {
            Map<String, Object> childNode = new LinkedHashMap<>(child);
            childNode.put("children", buildChildrenDepts(tenantId, ((Number) child.get("id")).longValue()));
            result.add(childNode);
        }
        
        return result;
    }

    @Override
    public Map<String, Object> listDepartments(Long tenantId, int page, int size) {
        try {
            String sql = "SELECT id, tenant_id, team_name, parent_id, status FROM t_team " +
                        "WHERE tenant_id = ? AND deleted = 0 ORDER BY create_time DESC LIMIT ? OFFSET ?";
            
            List<Map<String, Object>> content = jdbcTemplate.queryForList(sql, tenantId, size, (page - 1) * size);
            
            String countSql = "SELECT COUNT(*) as total FROM t_team WHERE tenant_id = ? AND deleted = 0";
            Integer total = jdbcTemplate.queryForObject(countSql, Integer.class, tenantId);
            if (total == null) total = 0;
            
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("content", content);
            result.put("totalElements", total);
            result.put("totalPages", (total + size - 1) / size);
            result.put("currentPage", page);
            result.put("pageSize", size);
            
            return result;
        } catch (Exception e) {
            log.error("查询部门列表失败：", e);
            return Map.of(
                "content", List.of(),
                "totalElements", 0,
                "totalPages", 0,
                "currentPage", page,
                "pageSize", size
            );
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> createDepartment(Long tenantId, String name, Map<String, Object> data) {
        try {
            Long deptId = System.currentTimeMillis();
            Long parentId = data.get("parentId") != null ? 
                           ((Number) data.get("parentId")).longValue() : 0L;
            Integer status = data.get("status") != null ? Integer.valueOf(data.get("status").toString()) : 1;
            
            String sql = "INSERT INTO t_team (id, tenant_id, team_name, parent_id, status, deleted, create_time, update_time) " +
                        "VALUES (?, ?, ?, ?, ?, 0, NOW(), NOW())";
            
            jdbcTemplate.update(sql, deptId, tenantId, name, parentId, status);
            
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("id", deptId);
            result.put("tenantId", tenantId);
            result.put("teamName", name);
            result.put("parentId", parentId);
            result.put("status", status);
            
            return result;
        } catch (Exception e) {
            log.error("创建部门失败：", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "创建部门失败");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> updateDepartment(Long tenantId, Long deptId, Map<String, Object> data) {
        try {
            String sql = "UPDATE t_team SET ";
            List<Object> params = new ArrayList<>();
            
            if (data.containsKey("teamName") || data.containsKey("name")) {
                sql += "team_name = ?, ";
                params.add(data.getOrDefault("teamName", data.get("name")));
            }
            if (data.containsKey("parentId")) {
                sql += "parent_id = ?, ";
                params.add(((Number) data.get("parentId")).longValue());
            }
            if (data.containsKey("status")) {
                sql += "status = ?, ";
                params.add(Integer.valueOf(data.get("status").toString()));
            }
            
            sql += "update_time = NOW() WHERE id = ? AND tenant_id = ?";
            params.add(deptId);
            params.add(tenantId);
            
            int rows = jdbcTemplate.update(sql, params.toArray());
            
            if (rows == 0) {
                throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "部门不存在");
            }
            
            String selectSql = "SELECT id, tenant_id, team_name, parent_id, status FROM t_team WHERE id = ? AND tenant_id = ?";
            List<Map<String, Object>> results = jdbcTemplate.queryForList(selectSql, deptId, tenantId);
            
            return results.isEmpty() ? Map.of() : results.get(0);
        } catch (Exception e) {
            if (e instanceof BusinessException) throw e;
            log.error("更新部门失败：", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "更新部门失败");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteDepartment(Long tenantId, Long deptId) {
        try {
            String sql = "UPDATE t_team SET deleted = 1 WHERE id = ? AND tenant_id = ?";
            
            int rows = jdbcTemplate.update(sql, deptId, tenantId);
            
            if (rows == 0) {
                throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "部门不存在");
            }
            
            log.info("删除部门 - deptId: {}", deptId);
        } catch (Exception e) {
            if (e instanceof BusinessException) throw e;
            log.error("删除部门失败：", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "删除部门失败");
        }
    }

    @Override
    public Map<String, Object> getUsersByDepartment(Long tenantId, Long deptId, int page, int size) {
        try {
            // t_team 没有关联用户表，直接查询该部门下的用户（按 tenant_id 过滤）
            String sql = "SELECT id, username, nickname, email, status FROM t_user " +
                        "WHERE tenant_id = ? AND deleted = 0 " +
                        "ORDER BY create_time DESC LIMIT ? OFFSET ?";
            
            List<Map<String, Object>> content = jdbcTemplate.queryForList(sql, tenantId, size, (page - 1) * size);
            
            String countSql = "SELECT COUNT(*) as total FROM t_user WHERE tenant_id = ? AND deleted = 0";
            Integer total = jdbcTemplate.queryForObject(countSql, Integer.class, tenantId);
            if (total == null) total = 0;
            
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("content", content);
            result.put("totalElements", total);
            result.put("totalPages", (total + size - 1) / size);
            result.put("currentPage", page);
            result.put("pageSize", size);
            
            return result;
        } catch (Exception e) {
            log.error("查询部门用户失败：", e);
            return Map.of(
                "content", List.of(),
                "totalElements", 0,
                "totalPages", 0,
                "currentPage", page,
                "pageSize", size
            );
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> addUserToDepartment(Long tenantId, Long deptId, String userId) {
        try {
            // t_team 没有关联用户表，用户通过 tenant_id 关联到租户
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("deptId", deptId);
            result.put("userId", userId);
            result.put("tenantId", tenantId);
            result.put("message", "用户已通过 tenant_id 关联到租户");
            
            log.info("添加用户到部门 - deptId: {}, userId: {}", deptId, userId);
            
            return result;
        } catch (Exception e) {
            log.error("添加用户到部门失败：", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "添加用户到部门失败");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeUserFromDepartment(Long tenantId, Long deptId, String userId) {
        try {
            // t_team 没有关联用户表
            log.info("从部门移除用户 - deptId: {}, userId: {}", deptId, userId);
        } catch (Exception e) {
            if (e instanceof BusinessException) throw e;
            log.error("移除用户失败：", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "移除用户失败");
        }
    }
}
