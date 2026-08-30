package com.example.zuoaiagent.tenant.service;

import java.util.List;
import java.util.Map;

/**
 * P30：部门服务接口
 */
public interface DepartmentService {

    /**
     * 获取部门树形结构
     */
    List<Map<String, Object>> getDepartmentTree(Long tenantId);

    /**
     * 分页查询部门列表
     */
    Map<String, Object> listDepartments(Long tenantId, int page, int size);

    /**
     * 创建部门
     */
    Map<String, Object> createDepartment(Long tenantId, String name, Map<String, Object> data);

    /**
     * 更新部门信息
     */
    Map<String, Object> updateDepartment(Long tenantId, Long deptId, Map<String, Object> data);

    /**
     * 删除部门
     */
    void deleteDepartment(Long tenantId, Long deptId);

    /**
     * 获取部门下的用户列表
     */
    Map<String, Object> getUsersByDepartment(Long tenantId, Long deptId, int page, int size);

    /**
     * 添加用户到部门
     */
    Map<String, Object> addUserToDepartment(Long tenantId, Long deptId, String userId);

    /**
     * 从部门移除用户
     */
    void removeUserFromDepartment(Long tenantId, Long deptId, String userId);
}
