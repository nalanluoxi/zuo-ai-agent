package com.example.authservice.service;

import cn.dev33.satoken.stp.SaTokenInfo;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.authservice.entity.*;
import com.example.authservice.mapper.*;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class AuthService {
    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    
    private final UserMapper userMapper;
    private final TenantMapper tenantMapper;
    private final AuditLogMapper auditLogMapper;
    private final UserService userService;
    private final JdbcTemplate jdbcTemplate;

    /** 默认组织（租户）ID：所有新用户注册即加入 */
    private static final long DEFAULT_TENANT_ID = 1L;

    @Transactional
    public SaTokenInfo register(String username, String password, String nickname) {
        if (userMapper.selectOne(new LambdaQueryWrapper<UserDO>().eq(UserDO::getUsername, username)) != null) {
            throw new RuntimeException("用户名已存在");
        }
        // 加入默认组织，不再为每个用户创建独立租户；
        // 默认组织已有系统意图节点（闲聊），无需重复初始化
        UserDO user = new UserDO();
        user.setUsername(username);
        user.setPassword(BCrypt.hashpw(password, BCrypt.gensalt()));
        user.setNickname(nickname);
        user.setTenantId(DEFAULT_TENANT_ID);
        user.setStatus(1);
        userMapper.insert(user);

        // 分配默认角色（普通用户）
        userService.assignDefaultRole(user.getId());

        StpUtil.login(user.getId());
        StpUtil.getSession().set("tenantId", DEFAULT_TENANT_ID);
        return StpUtil.getTokenInfo();
    }
    
    /**
     * 初始化默认系统意图节点（闲聊节点）
     * 注册租户时自动创建，用于处理非业务问题
     */
    private void initDefaultIntentNode(Long tenantId) {
        try {
            String sql = "INSERT INTO t_intent_node (id, tenant_id, parent_id, label, description, level, is_system, sort_order, enabled, deleted, create_time, update_time) " +
                    "VALUES (?, ?, NULL, ?, ?, 1, 1, 0, 1, 0, NOW(), NOW())";
            long nodeId = System.currentTimeMillis();
            jdbcTemplate.update(sql, nodeId, tenantId, "闲聊", "默认闲聊节点，处理非业务问题、问候、闲聊等");
            log.info("[AuthService] 租户 {} 初始化默认系统意图节点成功，nodeId={}", tenantId, nodeId);
        } catch (Exception e) {
            log.error("[AuthService] 初始化默认系统意图节点失败, tenantId={}: {}", tenantId, e.getMessage(), e);
            // 不抛出异常，避免影响注册流程
        }
    }

    public SaTokenInfo login(String username, String password, HttpServletRequest request) {
        UserDO user = userMapper.selectOne(new LambdaQueryWrapper<UserDO>().eq(UserDO::getUsername, username));
        if (user == null || !BCrypt.checkpw(password, user.getPassword())) {
            throw new RuntimeException("用户名或密码错误");
        }
        if (user.getStatus() == 0) {
            throw new RuntimeException("账号已被禁用");
        }
        StpUtil.login(user.getId());
        StpUtil.getSession().set("tenantId", user.getTenantId());

        AuditLogDO log = new AuditLogDO();
        log.setUserId(user.getId());
        log.setAction("LOGIN");
        log.setIpAddress(request.getRemoteAddr());
        log.setCreateTime(LocalDateTime.now());
        auditLogMapper.insert(log);

        return StpUtil.getTokenInfo();
    }

    public void logout() {
        StpUtil.logout();
    }

    public UserDO currentUser() {
        return userMapper.selectById(StpUtil.getLoginIdAsLong());
    }

    public List<String> getUserRoleCodes(Long userId) {
        return userService.getUserRoleCodes(userId);
    }

    /**
     * 获取用户页面权限（多角色合并，SUPER_ADMIN 返回全部页面 ADMIN）
     */
    public List<Map<String, String>> getUserPagePermissions(Long userId) {
        return userService.getUserPagePermissions(userId);
    }

    /**
     * 获取用户角色名称列表（"我的信息"展示用）
     */
    public List<String> getUserRoleNames(Long userId) {
        return userService.getUserRoleNames(userId);
    }

    /**
     * 获取用户隶属部门的完整层级路径列表（"我的信息"展示用）
     */
    public List<String> getUserTeamPaths(Long userId) {
        return userService.getUserTeamPaths(userId);
    }

    /**
     * 获取用户的部门归属（含部门内角色）
     */
    public List<Map<String, Object>> getUserTeams(Long userId) {
        return userService.getUserTeams(userId);
    }
}