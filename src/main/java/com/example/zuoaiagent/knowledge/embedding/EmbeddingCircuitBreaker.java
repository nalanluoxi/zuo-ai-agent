package com.example.zuoaiagent.knowledge.embedding;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Embedding 模型熔断器
 *
 * <p>基于 {@link ConcurrentHashMap} 对每个候选模型独立维护三态熔断状态：
 * <ul>
 *   <li>{@code CLOSED}：正常，允许调用</li>
 *   <li>{@code OPEN}：熔断中，拒绝调用；超过 {@code openDurationMs} 后自动转为 HALF_OPEN</li>
 *   <li>{@code HALF_OPEN}：半开，放行一个探测请求；成功回 CLOSED，失败重回 OPEN</li>
 * </ul>
 *
 * <p>线程安全：状态变更使用 {@code synchronized} + CAS，适合低并发场景（Embedding 入库）。
 *
 * <p>参照 ragent {@code infra-ai} 模块的 {@code ModelHealthStore}。
 */
@Component
public class EmbeddingCircuitBreaker {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingCircuitBreaker.class);

    /** 熔断器三态枚举 */
    private enum State {CLOSED, OPEN, HALF_OPEN}

    /** 单个候选模型的熔断状态 */
    private static class BreakerEntry {
        volatile State state = State.CLOSED;
        /** 连续失败计数 */
        final AtomicInteger failureCount = new AtomicInteger(0);
        /** OPEN 状态开始时间（毫秒时间戳） */
        final AtomicLong openedAt = new AtomicLong(0);
        /** 是否已有探测请求在途（HALF_OPEN 防并发） */
        volatile boolean probeInFlight = false;
    }

    /** candidateId → 熔断状态条目 */
    private final ConcurrentHashMap<String, BreakerEntry> stateMap = new ConcurrentHashMap<>();

    private final EmbeddingProperties properties;

    public EmbeddingCircuitBreaker(EmbeddingProperties properties) {
        this.properties = properties;
    }

    // -------------------- 公共 API --------------------

    /**
     * 判断是否允许对指定候选模型发起调用。
     *
     * <ul>
     *   <li>CLOSED → 允许</li>
     *   <li>OPEN → 检查是否已过 openDurationMs；若是则转为 HALF_OPEN 并放行一个探测；否则拒绝</li>
     *   <li>HALF_OPEN → 仅当没有探测在途时放行，否则拒绝</li>
     * </ul>
     *
     * @param candidateId 候选模型标识
     * @return {@code true} 表示允许调用
     */
    public synchronized boolean allowCall(String candidateId) {
        BreakerEntry entry = stateMap.computeIfAbsent(candidateId, id -> new BreakerEntry());

        switch (entry.state) {
            case CLOSED:
                return true;

            case OPEN:
                long elapsed = System.currentTimeMillis() - entry.openedAt.get();
                if (elapsed >= properties.getSelection().getOpenDurationMs()) {
                    // 超过等待窗口，进入半开探测
                    log.info("[熔断器] 模型 {} OPEN → HALF_OPEN，开始探测", candidateId);
                    entry.state = State.HALF_OPEN;
                    entry.probeInFlight = true;
                    return true;
                }
                log.debug("[熔断器] 模型 {} 处于 OPEN 状态，跳过（还需等待 {} ms）",
                        candidateId, properties.getSelection().getOpenDurationMs() - elapsed);
                return false;

            case HALF_OPEN:
                if (!entry.probeInFlight) {
                    entry.probeInFlight = true;
                    return true;
                }
                // 已有探测在途，拒绝其他并发调用
                return false;

            default:
                return true;
        }
    }

    /**
     * 标记调用成功：重置失败计数，将状态恢复为 CLOSED。
     *
     * @param candidateId 候选模型标识
     */
    public synchronized void markSuccess(String candidateId) {
        BreakerEntry entry = stateMap.get(candidateId);
        if (entry == null) return;

        if (entry.state != State.CLOSED) {
            log.info("[熔断器] 模型 {} 调用成功，状态恢复为 CLOSED", candidateId);
        }
        entry.state = State.CLOSED;
        entry.failureCount.set(0);
        entry.probeInFlight = false;
    }

    /**
     * 标记调用失败：递增失败计数，达到阈值时进入 OPEN 状态。
     *
     * @param candidateId 候选模型标识
     */
    public synchronized void markFailure(String candidateId) {
        BreakerEntry entry = stateMap.computeIfAbsent(candidateId, id -> new BreakerEntry());

        int failures = entry.failureCount.incrementAndGet();
        entry.probeInFlight = false;

        int threshold = properties.getSelection().getFailureThreshold();
        if (failures >= threshold && entry.state != State.OPEN) {
            entry.state = State.OPEN;
            entry.openedAt.set(System.currentTimeMillis());
            log.warn("[熔断器] 模型 {} 连续失败 {} 次，进入 OPEN 状态，将在 {} ms 后尝试恢复",
                    candidateId, failures, properties.getSelection().getOpenDurationMs());
        }
    }
}
