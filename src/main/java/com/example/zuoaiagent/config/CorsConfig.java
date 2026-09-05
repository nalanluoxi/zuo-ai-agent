package com.example.zuoaiagent.config;

import com.example.zuoaiagent.monitor.trace.TraceInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class CorsConfig implements WebMvcConfigurer {

    private final AuthClientInterceptor authClientInterceptor;
    private final PagePermissionInterceptor pagePermissionInterceptor;
    private final TraceInterceptor traceInterceptor;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // 覆盖所有请求
        registry.addMapping("/**")
                // 允许发送 Cookie
                .allowCredentials(true)
                // 放行哪些域名（必须用 patterns，否则 * 会和 allowCredentials 冲突）
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("*");
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 方案 B：zuo-ai-agent 通过 auth-service 验证 token
        registry.addInterceptor(authClientInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/health",
                        "/swagger-ui/**",
                        "/swagger-resources/**",
                        "/v3/api-docs/**",
                        "/doc.html/**",
                        "/webjars/**"
                );

        // 页面级权限拦截（依赖 AuthClientInterceptor 已写入的上下文）
        registry.addInterceptor(pagePermissionInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/health",
                        "/swagger-ui/**",
                        "/swagger-resources/**",
                        "/v3/api-docs/**",
                        "/doc.html/**",
                        "/webjars/**"
                );

        // Trace 拦截器（自动打点）
        registry.addInterceptor(traceInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/health",
                        "/swagger-ui/**",
                        "/swagger-resources/**",
                        "/v3/api-docs/**",
                        "/doc.html/**",
                        "/webjars/**"
                );
    }
}
