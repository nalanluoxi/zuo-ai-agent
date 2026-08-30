package com.example.authservice.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.authservice.entity.*;
import com.example.authservice.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RoleService {
    private final RoleMapper roleMapper;
    private final RolePermissionMapper rolePermissionMapper;

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
        role.setRoleCode(roleCode); role.setRoleName(roleName);
        role.setScopeType(scopeType); role.setDataScope(dataScope);
        roleMapper.insert(role);
        return role;
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
        return rolePermissionMapper.selectList(new LambdaQueryWrapper<>()).stream()
                .filter(rp -> permIds.contains(rp.getPermissionId())).map(rp -> {
                    PermissionDO p = new PermissionDO();
                    p.setId(rp.getPermissionId());
                    return p;
                }).toList();
    }
}