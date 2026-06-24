package com.example.zuoaiagent.demo;

import com.example.zuoaiagent.chat.ChatCircuitBreaker;
import com.example.zuoaiagent.chat.ChatModelProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ChatCircuitBreaker 三态状态机单元测试（不启动 Spring 容器）
 *
 * <p>覆盖以下场景：
 * <ol>
 *   <li>初始 CLOSED 状态允许调用</li>
 *   <li>连续失败达阈值后进入 OPEN，拒绝调用</li>
 *   <li>OPEN 超时后转为 HALF_OPEN，放行探测</li>
 *   <li>HALF_OPEN 探测成功后恢复 CLOSED</li>
 *   <li>HALF_OPEN 探测失败后重回 OPEN</li>
 *   <li>HALF_OPEN 防并发：只放行一个探测</li>
 *   <li>成功调用重置失败计数</li>
 *   <li>多候选独立维护熔断状态</li>
 * </ol>
 */
class ChatCircuitBreakerTest {

    private static final int THRESHOLD = 2;
    private static final long OPEN_DURATION_MS = 200;

    private ChatCircuitBreaker circuitBreaker;

    @BeforeEach
    void setUp() {
        ChatModelProperties.Selection selection = new ChatModelProperties.Selection();
        selection.setFailureThreshold(THRESHOLD);
        selection.setOpenDurationMs(OPEN_DURATION_MS);
        ChatModelProperties properties = new ChatModelProperties();
        properties.setSelection(selection);
        circuitBreaker = new ChatCircuitBreaker(properties);
    }

    @Test
    @DisplayName("场景1：初始 CLOSED 状态 - 允许调用")
    void testInitialClosedAllowsCall() {
        assertTrue(circuitBreaker.allowCall("model-a"), "初始状态应为 CLOSED，允许调用");
        System.out.println("[场景1] 初始 CLOSED 状态允许调用 ✓");
    }

    @Test
    @DisplayName("场景2：连续失败达阈值 - 进入 OPEN，拒绝调用")
    void testOpenAfterThresholdFailures() {
        circuitBreaker.markFailure("model-a");
        assertTrue(circuitBreaker.allowCall("model-a"), "第1次失败后仍应允许（未达阈值）");

        circuitBreaker.markFailure("model-a");
        assertFalse(circuitBreaker.allowCall("model-a"), "达到阈值后应进入 OPEN，拒绝调用");

        System.out.println("[场景2] 连续失败 " + THRESHOLD + " 次触发熔断，OPEN 状态拒绝调用 ✓");
    }

    @Test
    @DisplayName("场景3：OPEN 超时 - 转为 HALF_OPEN，放行探测")
    void testHalfOpenAfterTimeout() throws InterruptedException {
        circuitBreaker.markFailure("model-a");
        circuitBreaker.markFailure("model-a");
        assertFalse(circuitBreaker.allowCall("model-a"), "OPEN 状态应拒绝");
        Thread.sleep(OPEN_DURATION_MS + 50);
        assertTrue(circuitBreaker.allowCall("model-a"), "超时后应转为 HALF_OPEN，放行探测");
        System.out.println("[场景3] OPEN 超时后转为 HALF_OPEN，放行探测 ✓");
    }

    @Test
    @DisplayName("场景4：HALF_OPEN 探测成功 - 恢复 CLOSED")
    void testHalfOpenRecoveryOnSuccess() throws InterruptedException {
        circuitBreaker.markFailure("model-a");
        circuitBreaker.markFailure("model-a");

        Thread.sleep(OPEN_DURATION_MS + 50);
        assertTrue(circuitBreaker.allowCall("model-a"), "应转为 HALF_OPEN");

        circuitBreaker.markSuccess("model-a");
        assertTrue(circuitBreaker.allowCall("model-a"), "探测成功后应回到 CLOSED，允许后续调用");

        System.out.println("[场景4] HALF_OPEN 探测成功，恢复 CLOSED ✓");
    }

    @Test
    @DisplayName("场景5：HALF_OPEN 探测失败 - 重回 OPEN")
    void testHalfOpenProbeFailureReturnsToOpen() throws InterruptedException {
        circuitBreaker.markFailure("model-a");
        circuitBreaker.markFailure("model-a");

        Thread.sleep(OPEN_DURATION_MS + 50);
        assertTrue(circuitBreaker.allowCall("model-a"), "应转为 HALF_OPEN");

        circuitBreaker.markFailure("model-a");
        assertFalse(circuitBreaker.allowCall("model-a"), "探测失败后应重回 OPEN，拒绝调用");

        System.out.println("[场景5] HALF_OPEN 探测失败，重回 OPEN ✓");
    }

    @Test
    @DisplayName("场景6：HALF_OPEN 防并发 - 只放行一个探测请求")
    void testHalfOpenOnlyOneProbeAllowed() throws InterruptedException {
        circuitBreaker.markFailure("model-a");
        circuitBreaker.markFailure("model-a");

        Thread.sleep(OPEN_DURATION_MS + 50);

        assertTrue(circuitBreaker.allowCall("model-a"), "第一个探测应被放行");
        assertFalse(circuitBreaker.allowCall("model-a"), "并发第二个探测应被拒绝");

        System.out.println("[场景6] HALF_OPEN 只放行一个探测，并发请求被拒绝 ✓");
    }

    @Test
    @DisplayName("场景7：成功调用重置失败计数")
    void testSuccessResetsFailureCount() {
        circuitBreaker.markFailure("model-a");
        assertTrue(circuitBreaker.allowCall("model-a"), "1次失败仍允许");

        circuitBreaker.markSuccess("model-a"); // 清零

        circuitBreaker.markFailure("model-a");
        assertTrue(circuitBreaker.allowCall("model-a"), "清零后1次失败仍允许");

        circuitBreaker.markFailure("model-a");
        assertFalse(circuitBreaker.allowCall("model-a"), "重新积累到阈值才熔断");

        System.out.println("[场景7] 成功后失败计数清零，重新积累到阈值才熔断 ✓");
    }

    @Test
    @DisplayName("场景8：多候选独立维护熔断状态")
    void testMultipleCandidatesIndependentState() {
        // model-a 进入 OPEN
        circuitBreaker.markFailure("model-a");
        circuitBreaker.markFailure("model-a");

        assertFalse(circuitBreaker.allowCall("model-a"), "model-a 应进入 OPEN");
        assertTrue(circuitBreaker.allowCall("model-b"), "model-b 不受 model-a 影响，仍应 CLOSED");

        System.out.println("[场景8] 多候选独立维护熔断状态，互不影响 ✓");
    }
}
