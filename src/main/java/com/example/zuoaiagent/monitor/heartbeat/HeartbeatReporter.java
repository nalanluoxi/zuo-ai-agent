package com.example.zuoaiagent.monitor.heartbeat;

import com.example.zuoaiagent.monitor.trace.TraceManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.RuntimeMXBean;
import java.net.InetAddress;

/**
 * 心跳上报器 — 定时上报系统健康状态到 MQ
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HeartbeatReporter {

    private final RabbitTemplate rabbitTemplate;

    @Value("${spring.application.name:zuo-ai-agent}")
    private String serviceName;

    public static final String EXCHANGE_HEARTBEAT = "monitor.heartbeat";
    public static final String RK_HEARTBEAT = "heartbeat";

    @Scheduled(fixedRate = 30000) // 每 30 秒上报一次
    public void report() {
        try {
            MemoryMXBean memBean = ManagementFactory.getMemoryMXBean();
            RuntimeMXBean runtimeBean = ManagementFactory.getRuntimeMXBean();

            long usedMem = memBean.getHeapMemoryUsage().getUsed();
            long maxMem = memBean.getHeapMemoryUsage().getMax();
            double memUsage = maxMem > 0 ? (usedMem * 100.0) / maxMem : 0.0;

            // 简化 CPU 使用率：取系统平均负载 / 处理器数
            double cpuLoad = ManagementFactory.getOperatingSystemMXBean().getSystemLoadAverage();
            int processors = Runtime.getRuntime().availableProcessors();
            double cpuUsage = cpuLoad >= 0 ? (cpuLoad / processors) * 100.0 : 0.0;

            int threadCount = Thread.activeCount();
            long gcCount = ManagementFactory.getGarbageCollectorMXBeans().stream()
                    .mapToLong(gc -> gc.getCollectionCount())
                    .sum();

            String host = InetAddress.getLocalHost().getHostName();

            HeartbeatDTO dto = new HeartbeatDTO(
                    serviceName, host, "HEALTHY",
                    cpuUsage, memUsage,
                    threadCount, gcCount,
                    System.currentTimeMillis()
            );

            rabbitTemplate.convertAndSend(EXCHANGE_HEARTBEAT, RK_HEARTBEAT, dto);
        } catch (Exception e) {
            log.warn("心跳上报失败: {}", e.getMessage());
        }
    }

    public record HeartbeatDTO(
            String serviceName, String host, String status,
            Double cpuUsage, Double memoryUsage,
            Integer activeThreads, Long gcCount,
            long heartbeatTimeMs) {}
}
