package com.example.zuoaiagent.config;

import java.util.Map;

public class TenantContextHolder {
    private static final ThreadLocal<Long> TENANT_CONTEXT = new ThreadLocal<>();
    private static final ThreadLocal<Long> USER_CONTEXT = new ThreadLocal<>();
    /** 页面权限：pageCode → accessLevel（READ/WRITE/ADMIN） */
    private static final ThreadLocal<Map<String, String>> PAGE_PERM_CONTEXT = new ThreadLocal<>();

    public static void setTenantId(Long tenantId) {
        TENANT_CONTEXT.set(tenantId);
    }

    public static Long getTenantId() {
        return TENANT_CONTEXT.get();
    }

    public static void setUserId(Long userId) {
        USER_CONTEXT.set(userId);
    }

    public static Long getUserId() {
        return USER_CONTEXT.get();
    }

    public static void setPagePermissions(Map<String, String> pagePermissions) {
        PAGE_PERM_CONTEXT.set(pagePermissions);
    }

    public static Map<String, String> getPagePermissions() {
        return PAGE_PERM_CONTEXT.get();
    }

    /**
     * 当前用户是否拥有指定页面的最低访问级别
     *
     * @param pageCode      页面编码
     * @param requiredLevel 最低级别（READ/WRITE/ADMIN）
     */
    public static boolean hasPageAccess(String pageCode, String requiredLevel) {
        Map<String, String> perms = PAGE_PERM_CONTEXT.get();
        if (perms == null) {
            return false;
        }
        String level = perms.get(pageCode);
        if (level == null) {
            return false;
        }
        return rank(level) >= rank(requiredLevel);
    }

    private static int rank(String level) {
        return switch (level) {
            case "ADMIN" -> 3;
            case "WRITE" -> 2;
            default -> 1;
        };
    }

    public static void clear() {
        TENANT_CONTEXT.remove();
        USER_CONTEXT.remove();
        PAGE_PERM_CONTEXT.remove();
    }
}
