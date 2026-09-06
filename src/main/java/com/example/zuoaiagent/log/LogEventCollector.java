package com.example.zuoaiagent.log;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 编程式日志事件打点工具
 *
 * <p>用于在代码中手动记录 event 类型日志，支持关联 trace node_id。
 *
 * <p>使用示例：
 * <pre>
 * logEventCollector.logEvent("HyDE_GENERATE", nodeId,
 *     Map.of("query", query, "hydeDoc", doc));
 * </pre>
 */
@Component
public class LogEventCollector {

    private static final Logger log = LoggerFactory.getLogger(LogEventCollector.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 记录事件日志
     *
     * @param eventType 事件类型（如 HYDE_GENERATE, INTENT_CLASSIFY）
     * @param nodeId    关联的 trace node ID（可为 null）
     * @param metadata  扩展元数据
     */
    public void logEvent(String eventType, Long nodeId, Map<String, Object> metadata) {
        String originalLogType = MDC.get("logType");
        String originalEventType = MDC.get("eventType");
        String originalMetadata = MDC.get("metadata");

        try {
            MDC.put("logType", "event");
            MDC.put("eventType", eventType);
            if (nodeId != null) {
                MDC.put("nodeId", String.valueOf(nodeId));
            }
            if (metadata != null && !metadata.isEmpty()) {
                MDC.put("metadata", toJson(metadata));
            }

            log.info("[EVENT] {}", eventType);
        } finally {
            // 恢复原始 MDC
            restoreMDC("logType", originalLogType);
            restoreMDC("eventType", originalEventType);
            restoreMDC("metadata", originalMetadata);
            MDC.remove("nodeId");
        }
    }

    /**
     * 记录事件日志（简化版，无 node_id）
     */
    public void logEvent(String eventType, Map<String, Object> metadata) {
        logEvent(eventType, null, metadata);
    }

    /**
     * 记录事件日志（最简版）
     */
    public void logEvent(String eventType) {
        logEvent(eventType, null, null);
    }

    /**
     * 记录带耗时的性能事件
     */
    public void logPerformanceEvent(String eventType, Long nodeId, long durationMs, Map<String, Object> extra) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("durationMs", durationMs);
        if (extra != null) {
            metadata.putAll(extra);
        }
        logEvent(eventType, nodeId, metadata);
    }

    private void restoreMDC(String key, String originalValue) {
        if (originalValue != null) {
            MDC.put(key, originalValue);
        } else {
            MDC.remove(key);
        }
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "{}";
        }
    }
}
