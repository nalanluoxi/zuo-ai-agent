package com.example.zuai.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.List;

/**
 * 全局认证过滤器
 * 验证请求中的 satoken 是否有效
 */
@Slf4j
@Component
public class AuthFilter implements GlobalFilter, Ordered {
    
    /**
     * 白名单路径（不需要认证）
     */
    private static final List<String> WHITE_LIST = Arrays.asList(
        "/api/auth/login",
        "/api/auth/register",
        "/api/auth/logout",
        "/api/health"
    );
    
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().value();
        
        // 检查是否在白名单中
        if (isWhiteListed(path)) {
            log.debug("白名单路径，跳过认证: {}", path);
            return chain.filter(exchange);
        }
        
        // 从请求头获取 satoken
        String saToken = request.getHeaders().getFirst("satoken");
        if (saToken == null || saToken.trim().isEmpty()) {
            // 尝试从查询参数获取
            saToken = request.getQueryParams().getFirst("satoken");
        }
        
        if (saToken == null || saToken.trim().isEmpty()) {
            log.warn("未提供 satoken，拒绝访问: {}", path);
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }
        
        // TODO: 调用 auth-service 验证 token 是否有效
        // 这里暂时只检查 token 是否存在，实际应该调用 auth-service 的 /me 接口验证
        // 可以通过 WebClient 调用 http://localhost:8100/api/auth/me 验证
        
        log.debug("satoken 验证通过: {}", path);
        
        // 将 satoken 传递给下游服务
        ServerHttpRequest mutatedRequest = request.mutate()
            .header("satoken", saToken)
            .build();
        
        return chain.filter(exchange.mutate().request(mutatedRequest).build());
    }
    
    @Override
    public int getOrder() {
        // 优先级越高（数字越小），过滤器越早执行
        return -100;
    }
    
    /**
     * 检查路径是否在白名单中
     */
    private boolean isWhiteListed(String path) {
        return WHITE_LIST.stream().anyMatch(path::startsWith);
    }
}
