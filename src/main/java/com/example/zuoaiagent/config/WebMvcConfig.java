package com.example.zuoaiagent.config;

import com.example.zuoaiagent.raglab.interceptor.RagGrayInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置：注册 RAG 灰度拦截器
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final RagGrayInterceptor ragGrayInterceptor;

    public WebMvcConfig(RagGrayInterceptor ragGrayInterceptor) {
        this.ragGrayInterceptor = ragGrayInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 只对 RAG 对话接口启用灰度分流
        registry.addInterceptor(ragGrayInterceptor)
                .addPathPatterns("/stream/smart", "/stream/rag/pipeline", "/chat/**")
                .excludePathPatterns("/api/health", "/admin/**");
    }
}
