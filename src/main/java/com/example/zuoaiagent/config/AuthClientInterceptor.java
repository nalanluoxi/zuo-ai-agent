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

            // 2. 提取 tenantId 并设置到 TenantContextHolder
            if (result != null && "0".equals(String.valueOf(result.get("code")))) {
                @SuppressWarnings("unchecked")
                Map<String, Object> data = (Map<String, Object>) result.get("data");
                if (data != null && data.get("tenantId") != null) {
                    Long tenantId = Long.valueOf(data.get("tenantId").toString());
                    TenantContextHolder.setTenantId(tenantId);
                    log.debug("设置租户上下文: tenantId={}", tenantId);
                }
            }

            return true;
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