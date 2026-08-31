package com.example.zuai.gateway.filter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 全局过滤器（方案 B：只做路由转发，不做认证）
 * 认证由 auth-service 负责，Gateway 只负责路由
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().value();

        // 方案 B：Gateway 只做路由转发，不做认证
        // 将请求头中的 satoken 传递给下游服务（如果有）
        String saToken = request.getHeaders().getFirst("satoken");
        if (saToken == null || saToken.trim().isEmpty()) {
            // 尝试从查询参数获取（支持 EventSource SSE）
            saToken = request.getQueryParams().getFirst("satoken");
        }

        log.debug("路由转发: {}", path);

        // 如果有 token，传递给下游服务
        if (saToken != null && !saToken.trim().isEmpty()) {
            ServerHttpRequest mutatedRequest = request.mutate()
                .header("satoken", saToken)
                .build();
            return chain.filter(exchange.mutate().request(mutatedRequest).build());
        }

        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return -100;
    }
}
