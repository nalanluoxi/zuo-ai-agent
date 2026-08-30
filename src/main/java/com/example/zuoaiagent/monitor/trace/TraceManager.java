package com.example.zuoaiagent.monitor.trace;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Trace 管理器 — 创建/结束 span 并发布到 MQ
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TraceManager {

    private final RabbitTemplate rabbitTemplate;

    @Value("${spring.application.name:zuo-ai-agent}")
    private String serviceName;

    public static final String EXCHANGE_TRACE = "monitor.trace";
    public static final String RK_TRACE = "span";

    /** 开始一个新的 span（设置 TraceContext） */
    public void startSpan(String operationName) {
        TraceContext ctx = TraceContext.current();
        ctx.setTraceId(ctx.getTraceId() != null ? ctx.getTraceId() : generateTraceId());
        ctx.setParentSpanId(ctx.getSpanId());
        ctx.setSpanId(generateSpanId());
        ctx.setOperationName(operationName);
        ctx.setStartTimeMs(System.currentTimeMillis());
    }

    /** 结束当前 span 并发布到 MQ */
    public void endSpan(String status, String tags) {
        TraceContext ctx = TraceContext.current();
        long durationMs = System.currentTimeMillis() - ctx.getStartTimeMs();

        try {
            SpanDTO dto = new SpanDTO(
                    ctx.getTraceId(), ctx.getSpanId(), ctx.getParentSpanId(),
                    ctx.getOperationName(), serviceName,
                    ctx.getStartTimeMs(), (double) durationMs,
                    status != null ? status : "OK", tags
            );
            rabbitTemplate.convertAndSend(EXCHANGE_TRACE, RK_TRACE, dto);
        } catch (Exception e) {
            log.warn("发送 trace span 到 MQ 失败: {}", e.getMessage());
        } finally {
            // 回退到 parent span context
            ctx.setSpanId(ctx.getParentSpanId());
            ctx.setParentSpanId(null);
            ctx.setOperationName(null);
            ctx.setStartTimeMs(0);
        }
    }

    /** 便捷方法：记录一个完整的 span */
    public void recordSpan(String operationName, Runnable action) {
        startSpan(operationName);
        try {
            action.run();
            endSpan("OK", null);
        } catch (Exception e) {
            endSpan("ERROR", "error=" + e.getClass().getSimpleName());
            throw e;
        }
    }

    private String generateTraceId() { return UUID.randomUUID().toString().replace("-", ""); }
    private String generateSpanId() { return UUID.randomUUID().toString().replace("-", "").substring(0, 16); }

    public record SpanDTO(
            String traceId, String spanId, String parentSpanId,
            String operationName, String serviceName,
            long startTimeMs, Double durationMs, String status, String tags) {}
}
