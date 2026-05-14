package com.example.zuoaiagent.chat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Chat 模型熔断器
 *
 * <p>基于 {@link ConcurrentHashMap} 对每个候选模型独立维护三态熔断状态：
 * <ul>
 *   <li>{@code CLOSED}：正常，允许调用</li>
 *   <li>{@code OPEN}：熔断中，拒绝调用；超过 {@code openDurationMs} 后自动转为 HALF_OPEN</li>
 *   <li>{@code HALF_OPEN}：半开，放行一个探测请求；成功回 CLOSED，失败重回 OPEN</li>
 * </ul>
 *
 * <p>参照 {@code EmbeddingCircuitBreaker} 的设计，结构完全一致，依赖 {@link ChatModelProperties}。
 */
@Component
public class ChatCircuitBreaker {

    private static final Logger log = LoggerFactory.getLogger(ChatCircuitBreaker.class);

    private enum State {CLOSED, OPEN, HALF_OPEN}

    private static class BreakerEntry {
        volatile State state = State.CLOSED;
        final AtomicInteger failureCount = new AtomicInteger(0);
        final AtomicLong openedAt = new AtomicLong(0);
        volatile boolean probeInFlight = false;
    }

    private final ConcurrentHashMap<String, BreakerEntry> stateMap = new ConcurrentHashMap<>();
    private final ChatModelProperties properties;

    public ChatCircuitBreaker(ChatModelProperties properties) {
        this.properties = properties;
    }

    /**
     * 判断是否允许对指定候选模型发起调用。
     */
    public synchronized boolean allowCall(String candidateId) {
        BreakerEntry entry = stateMap.computeIfAbsent(candidateId, id -> new BreakerEntry());

        switch (entry.state) {
            case CLOSED:
                return true;

            case OPEN:
                long elapsed = System.currentTimeMillis() - entry.openedAt.get();
                if (elapsed >= properties.getSelection().getOpenDurationMs()) {
                    log.info("[Chat熔断器] 模型 {} OPEN → HALF_OPEN，开始探测", candidateId);
                    entry.state = State.HALF_OPEN;
                    entry.probeInFlight = true;
                    return true;
                }
                log.debug("[Chat熔断器] 模型 {} 处于 OPEN 状态，跳过（还需等待 {} ms）",
                        candidateId, properties.getSelection().getOpenDurationMs() - elapsed);
                return false;

            case HALF_OPEN:
                if (!entry.probeInFlight) {
                    entry.probeInFlight = true;
                    return true;
                }
                return false;

            default:
                return true;
        }
    }

    /**
     * 标记调用成功：重置失败计数，将状态恢复为 CLOSED。
     */
    public synchronized void markSuccess(String candidateId) {
        BreakerEntry entry = stateMap.get(candidateId);
        if (entry == null) return;

        if (entry.state != State.CLOSED) {
            log.info("[Chat熔断器] 模型 {} 调用成功，状态恢复为 CLOSED", candidateId);
        }
        entry.state = State.CLOSED;
        entry.failureCount.set(0);
        entry.probeInFlight = false;
    }

    /**
     * 标记调用失败：递增失败计数，达到阈值时进入 OPEN 状态。
     */
    public synchronized void markFailure(String candidateId) {
        BreakerEntry entry = stateMap.computeIfAbsent(candidateId, id -> new BreakerEntry());

        int failures = entry.failureCount.incrementAndGet();
        entry.probeInFlight = false;

        int threshold = properties.getSelection().getFailureThreshold();
        if (failures >= threshold && entry.state != State.OPEN) {
            entry.state = State.OPEN;
            entry.openedAt.set(System.currentTimeMillis());
            log.warn("[Chat熔断器] 模型 {} 连续失败 {} 次，进入 OPEN 状态，将在 {} ms 后尝试恢复",
                    candidateId, failures, properties.getSelection().getOpenDurationMs());
        }
    }
}
