package com.example.zuoaiagent.eval;

import com.example.zuoaiagent.chat.ChatCircuitBreaker;
import com.example.zuoaiagent.chat.ChatModelProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 熔断路由工程验证测试
 *
 * <p>不需要启动 Spring 容器，纯 Java 单元测试（毫秒级）。
 *
 * <p>验证以下工程要点（对应面试陈述第3条）：
 * <ol>
 *   <li>三态状态机：CLOSED → OPEN → HALF_OPEN → CLOSED 完整回路</li>
 *   <li>失败阈值触发熔断（参照 Resilience4j 设计思路）</li>
 *   <li>openDurationMs 超时后自动半开探测</li>
 *   <li>探测成功恢复 / 探测失败重新熔断</li>
 *   <li>多候选模型独立维护状态（支持多供应商切换）</li>
 *   <li>markSuccess 重置失败计数（防止偶发错误累积）</li>
 * </ol>
 *
 * <p>执行方式：
 * <pre>
 *   mvn test -Dtest=RoutingCircuitBreakerIntegrationTest -pl .
 * </pre>
 */
class RoutingCircuitBreakerIntegrationTest {

    private static final int THRESHOLD = 3;
    private static final long OPEN_DURATION_MS = 300;

    private ChatCircuitBreaker breaker;

    @BeforeEach
    void setUp() {
        ChatModelProperties.Selection sel = new ChatModelProperties.Selection();
        sel.setFailureThreshold(THRESHOLD);
        sel.setOpenDurationMs(OPEN_DURATION_MS);
        ChatModelProperties props = new ChatModelProperties();
        props.setSelection(sel);
        breaker = new ChatCircuitBreaker(props);
    }

    // ── 测试1：CLOSED → OPEN 触发条件 ─────────────────────────────────────────

    @Test
    @DisplayName("熔断1：CLOSED 初始状态 - 未达阈值不熔断")
    void testClosedDoesNotTripBeforeThreshold() {
        for (int i = 0; i < THRESHOLD - 1; i++) {
            breaker.markFailure("qwen");
            assertTrue(breaker.allowCall("qwen"),
                    "失败 " + (i + 1) + " 次，未达阈值 " + THRESHOLD + "，应仍然允许调用");
        }
        System.out.println("  ✅ CLOSED 阶段：连续 " + (THRESHOLD - 1) + " 次失败，未达阈值不熔断");
    }

    @Test
    @DisplayName("熔断2：CLOSED → OPEN - 失败达阈值时熔断，后续请求全部拒绝")
    void testTripsToOpenOnThreshold() {
        for (int i = 0; i < THRESHOLD; i++) {
            breaker.markFailure("qwen");
        }
        assertFalse(breaker.allowCall("qwen"), "达到阈值后应进入 OPEN，拒绝调用");
        assertFalse(breaker.allowCall("qwen"), "OPEN 状态持续拒绝，不应因多次调用而改变");
        System.out.println("  ✅ CLOSED → OPEN：连续 " + THRESHOLD + " 次失败触发熔断，OPEN 拒绝所有请求");
    }

    // ── 测试2：OPEN → HALF_OPEN 超时探测 ─────────────────────────────────────

    @Test
    @DisplayName("熔断3：OPEN → HALF_OPEN - 超过 openDurationMs 后放行探测请求")
    void testOpenToHalfOpenAfterTimeout() throws InterruptedException {
        triggerOpen("qwen");
        assertFalse(breaker.allowCall("qwen"), "OPEN 阶段应拒绝");

        Thread.sleep(OPEN_DURATION_MS + 50);

        assertTrue(breaker.allowCall("qwen"), "超时后应转为 HALF_OPEN，放行1个探测");
        assertFalse(breaker.allowCall("qwen"), "HALF_OPEN 只允许1个探测，第2个应拒绝");
        System.out.println("  ✅ OPEN → HALF_OPEN：超时后探测放行，防止并发多探测");
    }

    // ── 测试3：HALF_OPEN 恢复路径 ─────────────────────────────────────────────

    @Test
    @DisplayName("熔断4：HALF_OPEN → CLOSED - 探测成功完全恢复，后续请求正常通过")
    void testHalfOpenRecoversToClosed() throws InterruptedException {
        triggerOpen("qwen");
        Thread.sleep(OPEN_DURATION_MS + 50);

        assertTrue(breaker.allowCall("qwen"), "探测请求放行");
        breaker.markSuccess("qwen");

        assertTrue(breaker.allowCall("qwen"), "探测成功后应恢复 CLOSED，后续正常放行");
        assertTrue(breaker.allowCall("qwen"), "CLOSED 状态持续允许");
        System.out.println("  ✅ HALF_OPEN → CLOSED：探测成功，供应商完全恢复");
    }

    @Test
    @DisplayName("熔断5：HALF_OPEN → OPEN - 探测失败重新熔断")
    void testHalfOpenRetriesToOpen() throws InterruptedException {
        triggerOpen("qwen");
        Thread.sleep(OPEN_DURATION_MS + 50);

        assertTrue(breaker.allowCall("qwen"), "探测请求放行");
        breaker.markFailure("qwen"); // 探测失败

        assertFalse(breaker.allowCall("qwen"), "探测失败后应重回 OPEN，继续拒绝");
        System.out.println("  ✅ HALF_OPEN → OPEN：探测失败，重新进入熔断");
    }

    // ── 测试4：多供应商独立状态（核心架构价值） ──────────────────────────────

    @Test
    @DisplayName("熔断6：多供应商独立熔断 - 主供应商熔断不影响备用供应商")
    void testMultiProviderIndependentState() {
        // 主供应商 qwen 熔断
        triggerOpen("dashscope-qwen");

        assertFalse(breaker.allowCall("dashscope-qwen"), "主供应商应熔断");
        assertTrue(breaker.allowCall("siliconflow-deepseek"), "备用供应商不受影响，应正常放行");
        assertTrue(breaker.allowCall("siliconflow-deepseek"), "备用供应商持续可用");

        System.out.println("  ✅ 多供应商独立熔断：主供应商故障后自动路由到备用，支撑多供应商切换架构");
    }

    // ── 测试5：markSuccess 重置，防止偶发错误累积 ─────────────────────────────

    @Test
    @DisplayName("熔断7：markSuccess 重置失败计数 - 偶发错误不会导致误熔断")
    void testSuccessResetsCounterPreventsAccumulation() {
        // 连续 THRESHOLD-1 次失败
        for (int i = 0; i < THRESHOLD - 1; i++) {
            breaker.markFailure("qwen");
        }
        // 一次成功（清零）
        breaker.markSuccess("qwen");

        // 再来 THRESHOLD-1 次失败，不应熔断
        for (int i = 0; i < THRESHOLD - 1; i++) {
            breaker.markFailure("qwen");
            assertTrue(breaker.allowCall("qwen"),
                    "成功后清零，再次失败 " + (i + 1) + " 次不应熔断");
        }

        // 再多1次才触发
        breaker.markFailure("qwen");
        assertFalse(breaker.allowCall("qwen"), "重新积累到阈值才熔断");
        System.out.println("  ✅ markSuccess 清零：偶发失败不累积，只有持续失败才触发熔断");
    }

    // ── 测试6：完整三态回路演示（面试亮点展示） ──────────────────────────────

    @Test
    @DisplayName("熔断8：完整状态机回路 CLOSED→OPEN→HALF_OPEN→CLOSED 全程演示")
    void testFullStateMachineLoop() throws InterruptedException {
        String model = "qwen-plus";

        System.out.println("\n  【完整三态回路演示】");

        // CLOSED
        assertTrue(breaker.allowCall(model));
        System.out.println("  初始 CLOSED ✅ → 允许调用");

        // CLOSED → OPEN
        triggerOpen(model);
        assertFalse(breaker.allowCall(model));
        System.out.println("  连续失败 " + THRESHOLD + " 次 → OPEN ✅ → 拒绝调用");

        // OPEN → HALF_OPEN
        Thread.sleep(OPEN_DURATION_MS + 50);
        assertTrue(breaker.allowCall(model));
        System.out.println("  等待 " + OPEN_DURATION_MS + "ms 超时 → HALF_OPEN ✅ → 放行探测");

        // HALF_OPEN → CLOSED
        breaker.markSuccess(model);
        assertTrue(breaker.allowCall(model));
        System.out.println("  探测成功 → CLOSED ✅ → 完全恢复");

        System.out.println("  ✅ 三态状态机完整回路验证通过（参照 Resilience4j 生产实践）");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  工具方法
    // ═══════════════════════════════════════════════════════════════════════════

    /** 快速触发熔断（填满失败计数到阈值） */
    private void triggerOpen(String candidateId) {
        for (int i = 0; i < THRESHOLD; i++) {
            breaker.markFailure(candidateId);
        }
    }
}
