package com.example.authservice.service;

import cn.hutool.crypto.digest.BCrypt;
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
public class UserService {
    private final UserMapper userMapper;
    private final UserRoleMapper userRoleMapper;
    private final UserTeamMapper userTeamMapper;
    private final RoleMapper roleMapper;

    public Page<UserDO> page(int current, int size, String keyword) {
        LambdaQueryWrapper<UserDO> qw = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            qw.like(UserDO::getUsername, keyword).or().like(UserDO::getNickname, keyword);
        }
        qw.orderByDesc(UserDO::getCreateTime);
        return userMapper.selectPage(new Page<>(current, size), qw);
    }

    public UserDO getById(Long id) { return userMapper.selectById(id); }

    @Transactional
    public UserDO create(String username, String password, String nickname, Long tenantId) {
        UserDO user = new UserDO();
        user.setUsername(username);
        user.setPassword(BCrypt.hashpw(password, BCrypt.gensalt()));
        user.setNickname(nickname);
        user.setTenantId(tenantId);
        user.setStatus(1);
        userMapper.insert(user);
        return user;
    }

    @Transactional
    public void update(Long id, String nickname, String email, Integer status) {
        UserDO user = userMapper.selectById(id);
        if (user == null) throw new RuntimeException("用户不存在");
        if (nickname != null) user.setNickname(nickname);
        if (email != null) user.setEmail(email);
        if (status != null) user.setStatus(status);
        userMapper.updateById(user);
    }

    @Transactional
    public void assignRoles(Long userId, List<Long> roleIds) {
        userRoleMapper.delete(new LambdaQueryWrapper<UserRoleDO>().eq(UserRoleDO::getUserId, userId));
        for (Long roleId : roleIds) {
            UserRoleDO ur = new UserRoleDO();
            ur.setUserId(userId); ur.setRoleId(roleId);
            userRoleMapper.insert(ur);
        }
    }

    public List<Long> getUserRoles(Long userId) {
        return userRoleMapper.selectList(new LambdaQueryWrapper<UserRoleDO>().eq(UserRoleDO::getUserId, userId))
                .stream().map(UserRoleDO::getRoleId).toList();
    }

    /**
     * 获取用户的角色编码列表（用于前端权限判断）
     */
    public List<String> getUserRoleCodes(Long userId) {
        List<Long> roleIds = getUserRoles(userId);
        if (roleIds.isEmpty()) return List.of();
        
        return roleMapper.selectList(new LambdaQueryWrapper<RoleDO>().in(RoleDO::getId, roleIds))
                .stream().map(RoleDO::getRoleCode).toList();
    }
}