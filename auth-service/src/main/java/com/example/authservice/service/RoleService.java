package com.example.authservice.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.authservice.common.BusinessException;
import com.example.authservice.entity.*;
import com.example.authservice.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class RoleService {
    private final RoleMapper roleMapper;
    private final RolePermissionMapper rolePermissionMapper;
    private final PermissionMapper permissionMapper;
    private final UserRoleMapper userRoleMapper;

    /** 内置角色（不可删除） */
    private static final List<String> BUILTIN_ROLES = List.of("SUPER_ADMIN", "USER");

    public Page<RoleDO> page(int current, int size) {
        return roleMapper.selectPage(new Page<>(current, size),
                new LambdaQueryWrapper<RoleDO>().orderByDesc(RoleDO::getCreateTime));
    }

    public List<RoleDO> listAll() {
        return roleMapper.selectList(new LambdaQueryWrapper<>());
    }

    @Transactional
    public RoleDO create(String roleCode, String roleName, String scopeType, String dataScope) {
        RoleDO role = new RoleDO();
        // roleCode 为空时后端自动生成（页面不暴露编码，仅作内部标识）
        if (roleCode == null || roleCode.isBlank()) {
            roleCode = "ROLE_" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
        }
        role.setRoleCode(roleCode); role.setRoleName(roleName);
        role.setScopeType(scopeType); role.setDataScope(dataScope);
        roleMapper.insert(role);
        return role;
    }

    /**
     * 删除角色（内置角色不可删），同时清理角色-权限、用户-角色关联
     */
    @Transactional
    public void deleteRole(Long roleId) {
        RoleDO role = roleMapper.selectById(roleId);
        if (role == null) {
            throw new BusinessException(404, "角色不存在");
        }
        if (BUILTIN_ROLES.contains(role.getRoleCode())) {
            throw new BusinessException(400, "内置角色不可删除");
        }
        rolePermissionMapper.delete(new LambdaQueryWrapper<RolePermissionDO>().eq(RolePermissionDO::getRoleId, roleId));
        userRoleMapper.delete(new LambdaQueryWrapper<UserRoleDO>().eq(UserRoleDO::getRoleId, roleId));
        roleMapper.deleteById(roleId);
    }

    @Transactional
    public void assignPermissions(Long roleId, List<Long> permIds) {
        rolePermissionMapper.delete(new LambdaQueryWrapper<RolePermissionDO>().eq(RolePermissionDO::getRoleId, roleId));
        for (Long permId : permIds) {
            RolePermissionDO rp = new RolePermissionDO();
            rp.setRoleId(roleId); rp.setPermissionId(permId);
            rolePermissionMapper.insert(rp);
        }
    }

    public List<PermissionDO> getRolePermissions(Long roleId) {
        List<Long> permIds = rolePermissionMapper.selectList(
                new LambdaQueryWrapper<RolePermissionDO>().eq(RolePermissionDO::getRoleId, roleId))
                .stream().map(RolePermissionDO::getPermissionId).toList();
        if (permIds.isEmpty()) return List.of();
        return permissionMapper.selectList(new LambdaQueryWrapper<PermissionDO>().in(PermissionDO::getId, permIds));
    }

    // ═══════════════ 页面级权限（resource_type = 'PAGE'） ═══════════════

    /**
     * 页面清单：t_permission 中 resource_type='PAGE' 的记录
     */
    public List<PermissionDO> listPages() {
        return permissionMapper.selectList(new LambdaQueryWrapper<PermissionDO>()
                .eq(PermissionDO::getResourceType, "PAGE")
                .orderByAsc(PermissionDO::getId));
    }

    /**
     * 角色列表（含每个角色的页面权限摘要）
     * @return [{id, roleCode, roleName, dataScope, pagePerms:[{pageCode, pageName, accessLevel}]}]
     */
    public List<Map<String, Object>> listWithPagePerms() {
        List<RoleDO> roles = listAll();
        List<PermissionDO> pages = listPages();
        Map<Long, PermissionDO> pageMap = new java.util.HashMap<>();
        pages.forEach(p -> pageMap.put(p.getId(), p));
        List<RolePermissionDO> allRps = rolePermissionMapper.selectList(new LambdaQueryWrapper<>());

        return roles.stream().map(role -> {
            List<Map<String, Object>> pagePerms = allRps.stream()
                    .filter(rp -> rp.getRoleId().equals(role.getId()) && pageMap.containsKey(rp.getPermissionId()))
                    .map(rp -> {
                        PermissionDO p = pageMap.get(rp.getPermissionId());
                        return Map.<String, Object>of(
                                "pageCode", p.getPermCode(),
                                "pageName", p.getPermName(),
                                "accessLevel", rp.getAccessLevel() == null ? "READ" : rp.getAccessLevel());
                    })
                    .toList();
            return Map.<String, Object>of(
                    "id", role.getId(),
                    "roleCode", role.getRoleCode() == null ? "" : role.getRoleCode(),
                    "roleName", role.getRoleName() == null ? "" : role.getRoleName(),
                    "dataScope", role.getDataScope() == null ? "" : role.getDataScope(),
                    "builtin", BUILTIN_ROLES.contains(role.getRoleCode()),
                    "pagePerms", pagePerms);
        }).toList();
    }

    /**
     * 查询角色的页面权限（含访问级别）
     * @return [{permissionId, pageCode, pageName, accessLevel}]
     */
    public List<Map<String, Object>> getRolePagePermissions(Long roleId) {
        List<RolePermissionDO> rps = rolePermissionMapper.selectList(
                new LambdaQueryWrapper<RolePermissionDO>().eq(RolePermissionDO::getRoleId, roleId));
        if (rps.isEmpty()) return List.of();
        List<Long> permIds = rps.stream().map(RolePermissionDO::getPermissionId).toList();
        Map<Long, PermissionDO> permMap = new java.util.HashMap<>();
        permissionMapper.selectList(new LambdaQueryWrapper<PermissionDO>()
                        .in(PermissionDO::getId, permIds)
                        .eq(PermissionDO::getResourceType, "PAGE"))
                .forEach(p -> permMap.put(p.getId(), p));
        return rps.stream()
                .filter(rp -> permMap.containsKey(rp.getPermissionId()))
                .map(rp -> {
                    PermissionDO p = permMap.get(rp.getPermissionId());
                    return Map.<String, Object>of(
                            "permissionId", p.getId(),
                            "pageCode", p.getPermCode(),
                            "pageName", p.getPermName(),
                            "accessLevel", rp.getAccessLevel() == null ? "READ" : rp.getAccessLevel());
                })
                .toList();
    }

    /**
     * 保存角色的页面权限（全量替换 PAGE 类权限，不影响其他类型权限）
     * 所有角色（含 SUPER_ADMIN/USER 内置角色）统一可配置
     * @param items [{permissionId, accessLevel}]
     */
    @Transactional
    public void saveRolePagePermissions(Long roleId, List<Map<String, Object>> items) {
        RoleDO role = roleMapper.selectById(roleId);
        if (role == null) {
            throw new BusinessException(404, "角色不存在");
        }
        // 仅删除 PAGE 类权限关联
        List<PermissionDO> pages = listPages();
        List<Long> pageIds = pages.stream().map(PermissionDO::getId).toList();
        if (!pageIds.isEmpty()) {
            rolePermissionMapper.delete(new LambdaQueryWrapper<RolePermissionDO>()
                    .eq(RolePermissionDO::getRoleId, roleId)
                    .in(RolePermissionDO::getPermissionId, pageIds));
        }
        if (items == null) return;
        for (Map<String, Object> item : items) {
            Object permId = item.get("permissionId");
            Object level = item.get("accessLevel");
            if (permId == null || level == null) continue;
            String accessLevel = String.valueOf(level);
            if (!List.of("READ", "WRITE", "ADMIN").contains(accessLevel)) continue;
            RolePermissionDO rp = new RolePermissionDO();
            rp.setRoleId(roleId);
            rp.setPermissionId(Long.valueOf(String.valueOf(permId)));
            rp.setAccessLevel(accessLevel);
            rolePermissionMapper.insert(rp);
        }
    }
}