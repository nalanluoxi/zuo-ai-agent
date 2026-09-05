package com.example.zuoaiagent.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Map;

@Component
public class AuthClientInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(AuthClientInterceptor.class);

    @Value("${auth.service.url:http://localhost:8100/api/auth}")
    private String authServiceUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 1. 读取 token：优先 Header，降级 query param（支持 EventSource SSE）
        String token = request.getHeader("satoken");
        if (token == null || token.isBlank()) {
            token = request.getParameter("satoken");
        }
        if (token == null || token.isBlank()) {
            response.setStatus(401);
            return false;
        }

        try {
            var headers = new org.springframework.http.HttpHeaders();
            headers.set("satoken", token);
            var entity = new org.springframework.http.HttpEntity<>(headers);
            @SuppressWarnings("unchecked")
            Map<String, Object> result = restTemplate.exchange(
                    authServiceUrl + "/me",
                    org.springframework.http.HttpMethod.GET,
                    entity,
                    Map.class
            ).getBody();

            // 2. 提取 tenantId 和 userId 并设置到上下文
            if (result != null && "0".equals(String.valueOf(result.get("code")))) {
                @SuppressWarnings("unchecked")
                Map<String, Object> data = (Map<String, Object>) result.get("data");
                if (data != null) {
                    if (data.get("tenantId") != null) {
                        Long tenantId = Long.valueOf(data.get("tenantId").toString());
                        TenantContextHolder.setTenantId(tenantId);
                        log.debug("设置租户上下文: tenantId={}", tenantId);
                    }
                    if (data.get("loginId") != null) {
                        Long userId = Long.valueOf(data.get("loginId").toString());
                        TenantContextHolder.setUserId(userId);
                        log.debug("设置用户上下文: userId={}", userId);
                    }
                    // 3. 提取页面权限列表 [{pageCode, accessLevel}] → Map<pageCode, accessLevel>
                    Object pagePermissions = data.get("pagePermissions");
                    if (pagePermissions instanceof java.util.List<?> list) {
                        Map<String, String> permMap = new java.util.HashMap<>();
                        for (Object item : list) {
                            if (item instanceof Map<?, ?> m
                                    && m.get("pageCode") != null && m.get("accessLevel") != null) {
                                permMap.put(String.valueOf(m.get("pageCode")), String.valueOf(m.get("accessLevel")));
                            }
                        }
                        TenantContextHolder.setPagePermissions(permMap);
                    }
                }

                return true;
            }

            // /me 返回非 0（token 无效或 auth-service 异常）：拒绝请求，
            // 不能放行——放行会导致后续业务在没有用户上下文的情况下写入数据（如无归属的对话）
            log.warn("Token 验证返回非 0 code，拒绝请求: {}", result != null ? result.get("code") : "null");
            response.setStatus(401);
            return false;
        } catch (Exception e) {
            log.warn("Token 验证失败: {}", e.getMessage());
            response.setStatus(401);
            return false;
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        // 请求结束后清理 ThreadLocal，防止线程池复用时数据泄露
        TenantContextHolder.clear();
    }
}