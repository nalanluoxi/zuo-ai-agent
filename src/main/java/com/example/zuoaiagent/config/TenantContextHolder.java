package com.example.zuoaiagent.config;

public class TenantContextHolder {
    private static final ThreadLocal<Long> TENANT_CONTEXT = new ThreadLocal<>();
    private static final ThreadLocal<Long> USER_CONTEXT = new ThreadLocal<>();

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

    public static void clear() {
        TENANT_CONTEXT.remove();
        USER_CONTEXT.remove();
    }
}
