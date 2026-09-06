package com.example.zuoaiagent.log;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import org.slf4j.MDC;

import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

/**
 * 日志监控 Appender
 *
 * <p>异步批量将日志通过 HTTP POST 发送到 log-monitor-service 的 /api/log/collect 接口。
 * 日志存入 BlockingQueue，满 batchSize 条或每 flushIntervalSeconds 秒触发一次 flush。
 */
public class LogMonitorAppender extends AppenderBase<ILoggingEvent> {

    private String serviceName = "unknown";
    private String logMonitorUrl = "http://localhost:8200/api/log/collect";
    private int batchSize = 100;
    private int flushIntervalSeconds = 3;
    private int queueSize = 10000;

    private final BlockingQueue<Map<String, Object>> queue;
    private final ScheduledExecutorService scheduler;
    private final HttpClient httpClient;
    private String hostName;

    public LogMonitorAppender() {
        this.queue = new LinkedBlockingQueue<>(queueSize);
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        try { this.hostName = InetAddress.getLocalHost().getHostName(); }
        catch (Exception e) { this.hostName = "unknown"; }
    }

    @Override
    public void start() {
        super.start();
        scheduler.scheduleAtFixedRate(this::flush, flushIntervalSeconds, flushIntervalSeconds, TimeUnit.SECONDS);
    }

    @Override
    public void stop() {
        scheduler.shutdown();
        flush();
        super.stop();
    }

    @Override
    protected void append(ILoggingEvent event) {
        if (!isStarted()) return;
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("serviceName", serviceName);
        entry.put("hostName", hostName);
        entry.put("traceId", MDC.get("traceId"));
        entry.put("logLevel", event.getLevel().toString());
        entry.put("loggerName", event.getLoggerName());
        entry.put("threadName", event.getThreadName());
        entry.put("message", event.getFormattedMessage());
        if (event.getThrowableProxy() != null) {
            entry.put("stackTrace", event.getThrowableProxy().getMessage());
        }
        entry.put("logTs", event.getInstant().toEpochMilli());

        // Phase 4: CAT 风格扩展字段（从 MDC 读取）
        String logType = MDC.get("logType");
        if (logType != null) entry.put("logType", logType);
        String spanId = MDC.get("spanId");
        if (spanId != null) entry.put("spanId", spanId);
        String parentTraceId = MDC.get("parentTraceId");
        if (parentTraceId != null) entry.put("parentTraceId", parentTraceId);
        String eventType = MDC.get("eventType");
        if (eventType != null) entry.put("eventType", eventType);
        String nodeId = MDC.get("nodeId");
        if (nodeId != null) {
            try { entry.put("nodeId", Long.parseLong(nodeId)); } catch (NumberFormatException ignored) {}
        }
        String metadata = MDC.get("metadata");
        if (metadata != null) entry.put("metadata", metadata);

        queue.offer(entry);
        if (queue.size() >= batchSize) {
            flush();
        }
    }

    private void flush() {
        List<Map<String, Object>> batch = new ArrayList<>();
        queue.drainTo(batch, batchSize);
        if (batch.isEmpty()) return;
        try {
            String json = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(Map.of("logs", batch));
            httpClient.send(HttpRequest.newBuilder()
                    .uri(URI.create(logMonitorUrl))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build(), HttpResponse.BodyHandlers.discarding());
        } catch (Exception ignored) {}
    }

    public void setServiceName(String serviceName) { this.serviceName = serviceName; }
    public void setLogMonitorUrl(String logMonitorUrl) { this.logMonitorUrl = logMonitorUrl; }
    public void setBatchSize(int batchSize) { this.batchSize = batchSize; }
    public void setFlushIntervalSeconds(int flushIntervalSeconds) { this.flushIntervalSeconds = flushIntervalSeconds; }
    public void setQueueSize(int queueSize) { this.queueSize = queueSize; }
}
