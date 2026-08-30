package com.example.zuoaiagent.monitor.trace;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * HTTP 请求级别的 Trace 拦截器 — 自动创建 root span
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TraceInterceptor implements HandlerInterceptor {

    private final TraceManager traceManager;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String traceId = request.getHeader("X-Trace-Id");
        String spanId = request.getHeader("X-Span-Id");

        TraceContext ctx = TraceContext.current();
        if (traceId != null) {
            ctx.setTraceId(traceId);
        }
        if (spanId != null) {
            ctx.setParentSpanId(spanId);
        }

        String operation = request.getMethod() + " " + request.getRequestURI();
        traceManager.startSpan(operation);

        // 回传 traceId 给客户端
        response.setHeader("X-Trace-Id", TraceContext.current().getTraceId());
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        String status = response.getStatus() >= 400 ? "ERROR" : "OK";
        String tags = ex != null ? "error=" + ex.getClass().getSimpleName() : null;
        traceManager.endSpan(status, tags);
        TraceContext.clear();
    }
}
