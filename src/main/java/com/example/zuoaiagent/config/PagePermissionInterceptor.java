package com.example.zuoaiagent.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 页面级权限拦截器
 * 按路径前缀映射页面编码：GET 需要 READ，写操作需要 WRITE；无权限返回 403。
 * 页面权限由 AuthClientInterceptor 从 auth-service /me 提取后放入 TenantContextHolder。
 */
@Component
public class PagePermissionInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(PagePermissionInterceptor.class);

    /**
     * 路径前缀 → 页面编码（按配置顺序匹配，更具体的前缀放前面）
     */
    private static final Map<String, String> PATH_PAGE_MAPPING = new LinkedHashMap<>() {{
        put("/rag-lab", "rag:lab");
        put("/monitor/redis", "monitor:redis");
        put("/monitor/db", "monitor:db");
        put("/admin", "manage:dashboard");
    }};

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        String path = resolvePath(request);
        String pageCode = matchPageCode(path);
        if (pageCode == null) {
            // 不在管控范围内的路径直接放行
            return true;
        }

        // GET/HEAD 需要只读权限，其余方法需要修改权限
        String method = request.getMethod();
        String requiredLevel = ("GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method)) ? "READ" : "WRITE";

        if (TenantContextHolder.hasPageAccess(pageCode, requiredLevel)) {
            return true;
        }

        Long userId = TenantContextHolder.getUserId();
        log.info("页面权限拒绝: userId={}, path={}, pageCode={}, required={}", userId, path, pageCode, requiredLevel);
        response.setStatus(403);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":403,\"message\":\"无权限访问该页面数据\"}");
        return false;
    }

    /**
     * 去掉 context-path（如 /api）后的应用内路径
     */
    private String resolvePath(HttpServletRequest request) {
        String path = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && path.startsWith(contextPath)) {
            path = path.substring(contextPath.length());
        }
        return path;
    }

    private String matchPageCode(String path) {
        for (Map.Entry<String, String> entry : PATH_PAGE_MAPPING.entrySet()) {
            if (path.startsWith(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }
}
