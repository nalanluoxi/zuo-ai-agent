package com.example.zuoaiagent.tenant.service;

import java.util.List;
import java.util.Map;

/**
 * P30：租户服务接口
 */
public interface TenantService {

    /**
     * 分页查询租户列表
     */
    Map<String, Object> listTenants(int page, int size);

    /**
     * 获取租户详情
     */
    Map<String, Object> getTenantDetail(Long tenantId);

    /**
     * 创建租户
     */
    Map<String, Object> createTenant(String name, Map<String, Object> data);

    /**
     * 更新租户信息
     */
    Map<String, Object> updateTenant(Long tenantId, Map<String, Object> data);

    /**
     * 删除租户
     */
    void deleteTenant(Long tenantId);

    /**
     * 获取租户的角色列表
     */
    List<Map<String, Object>> getRoles(Long tenantId);

    /**
     * 创建角色
     */
    Map<String, Object> createRole(Long tenantId, String roleName, Map<String, Object> data);

    /**
     * 为用户分配角色
     */
    void assignRoleToUser(Long tenantId, String userId, Long roleId);

    /**
     * 获取用户的角色列表
     */
    List<Map<String, Object>> getUserRoles(Long tenantId, String userId);
}
