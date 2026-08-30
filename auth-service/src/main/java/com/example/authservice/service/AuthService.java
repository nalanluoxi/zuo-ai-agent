package com.example.authservice.service;

import cn.dev33.satoken.stp.SaTokenInfo;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.authservice.entity.*;
import com.example.authservice.mapper.*;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserMapper userMapper;
    private final TenantMapper tenantMapper;
    private final AuditLogMapper auditLogMapper;
    private final UserService userService;

    @Transactional
    public SaTokenInfo register(String username, String password, String nickname) {
        if (userMapper.selectOne(new LambdaQueryWrapper<UserDO>().eq(UserDO::getUsername, username)) != null) {
            throw new RuntimeException("用户名已存在");
        }
        TenantDO tenant = new TenantDO();
        tenant.setTenantName(nickname + "的团队");
        tenant.setTenantCode("tenant_" + System.currentTimeMillis());
        tenant.setStatus(1);
        tenantMapper.insert(tenant);

        UserDO user = new UserDO();
        user.setUsername(username);
        user.setPassword(BCrypt.hashpw(password, BCrypt.gensalt()));
        user.setNickname(nickname);
        user.setTenantId(tenant.getId());
        user.setStatus(1);
        userMapper.insert(user);

        StpUtil.login(user.getId());
        StpUtil.getSession().set("tenantId", tenant.getId());
        return StpUtil.getTokenInfo();
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
}