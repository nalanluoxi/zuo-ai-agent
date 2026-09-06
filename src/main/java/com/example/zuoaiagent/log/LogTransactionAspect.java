package com.example.zuoaiagent.log;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * LogTransaction 注解的 AOP 切面
 *
 * <p>自动记录 transaction 类型日志，包含方法入参、返回值、耗时。
 * 通过 MDC 传递 logType/spanId/eventType 等字段给 LogMonitorAppender。
 */
@Aspect
@Component
public class LogTransactionAspect {

    private static final Logger log = LoggerFactory.getLogger(LogTransactionAspect.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Around("@annotation(logTransaction)")
    public Object around(ProceedingJoinPoint joinPoint, LogTransaction logTransaction) throws Throwable {
        String spanId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        String parentTraceId = MDC.get("traceId");

        // 设置 MDC 上下文
        MDC.put("logType", "transaction");
        MDC.put("spanId", spanId);
        if (parentTraceId != null) {
            MDC.put("parentTraceId", parentTraceId);
        }
        if (!logTransaction.eventType().isEmpty()) {
            MDC.put("eventType", logTransaction.eventType());
        }

        long startTime = System.currentTimeMillis();
        String methodName = joinPoint.getSignature().toShortString();
        String txName = logTransaction.name().isEmpty() ? methodName : logTransaction.name();

        // 记录开始日志
        if (logTransaction.logInput()) {
            try {
                String inputJson = truncate(toJson(joinPoint.getArgs()), logTransaction.maxInputLength());
                log.info("[TX-START] {} | input={}", txName, inputJson);
            } catch (Exception e) {
                log.info("[TX-START] {}", txName);
            }
        } else {
            log.info("[TX-START] {}", txName);
        }

        Object result = null;
        try {
            result = joinPoint.proceed();
            long duration = System.currentTimeMillis() - startTime;

            // 记录成功日志
            if (logTransaction.logOutput()) {
                try {
                    String outputJson = truncate(toJson(result), logTransaction.maxOutputLength());
                    log.info("[TX-END] {} | duration={}ms | output={}", txName, duration, outputJson);
                } catch (Exception e) {
                    log.info("[TX-END] {} | duration={}ms", txName, duration);
                }
            } else {
                log.info("[TX-END] {} | duration={}ms | status=SUCCESS", txName, duration);
            }

            // 记录 metadata
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("method", methodName);
            metadata.put("durationMs", duration);
            metadata.put("status", "SUCCESS");
            MDC.put("metadata", toJson(metadata));

            return result;
        } catch (Throwable t) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("[TX-END] {} | duration={}ms | status=ERROR | error={}",
                    txName, duration, t.getMessage());

            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("method", methodName);
            metadata.put("durationMs", duration);
            metadata.put("status", "ERROR");
            metadata.put("error", t.getMessage());
            MDC.put("metadata", toJson(metadata));

            throw t;
        } finally {
            // 清理 MDC
            MDC.remove("logType");
            MDC.remove("spanId");
            MDC.remove("parentTraceId");
            MDC.remove("eventType");
            MDC.remove("metadata");
        }
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return String.valueOf(obj);
        }
    }

    private String truncate(String text, int maxLength) {
        if (text == null) return null;
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength) + "...(truncated)";
    }
}
