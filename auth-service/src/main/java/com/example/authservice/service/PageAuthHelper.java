package com.example.authservice.service;

import cn.dev33.satoken.stp.StpUtil;
import com.example.authservice.common.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 页面级权限校验
 * 级别：READ(只读) < WRITE(修改) < ADMIN(超级管理)；SUPER_ADMIN 角色全部放行
 */
@Component
@RequiredArgsConstructor
public class PageAuthHelper {

    private final UserService userService;

    /** 要求只读权限 */
    public void checkRead(String pageCode) {
        check(pageCode, "READ");
    }

    /** 要求修改权限 */
    public void checkWrite(String pageCode) {
        check(pageCode, "WRITE");
    }

    /**
     * 校验当前登录用户对指定页面的访问级别
     * 所有角色（含 SUPER_ADMIN）统一按 t_role_permission 配置判定
     *
     * @param pageCode      页面编码（t_permission.perm_code，resource_type='PAGE'）
     * @param requiredLevel 需要的最低级别
     */
    public void check(String pageCode, String requiredLevel) {
        Long userId = StpUtil.getLoginIdAsLong();
        List<Map<String, String>> perms = userService.getUserPagePermissions(userId);
        String level = perms.stream()
                .filter(p -> pageCode.equals(p.get("pageCode")))
                .map(p -> p.get("accessLevel"))
                .findFirst()
                .orElse(null);
        if (level == null || rank(level) < rank(requiredLevel)) {
            throw new BusinessException(403, "无权限访问该页面数据");
        }
    }

    private static int rank(String level) {
        return switch (level) {
            case "ADMIN" -> 3;
            case "WRITE" -> 2;
            default -> 1;
        };
    }
}
