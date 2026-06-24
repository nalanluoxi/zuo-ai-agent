package com.example.zuoaiagent.demo;

import com.example.zuoaiagent.knowledge.embedding.CircuitBreakerEmbeddingModel;
import com.example.zuoaiagent.knowledge.embedding.EmbeddingCircuitBreaker;
import com.example.zuoaiagent.knowledge.embedding.EmbeddingModelFactory;
import com.example.zuoaiagent.knowledge.embedding.EmbeddingProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.EmbeddingResultMetadata;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 熔断路由机制单元测试（不启动 Spring 容器，全部用匿名类 Mock 替代真实模型）
 *
 * <p>覆盖以下场景：
 * <ol>
 *   <li>正常路由：请求打到队头候选</li>
 *   <li>自动切换：候选A失败后切换到候选B</li>
 *   <li>触发熔断：连续失败达到阈值后进入OPEN</li>
 *   <li>全部熔断：所有候选不可用时抛出异常</li>
 *   <li>HALF_OPEN探测恢复：超时后允许探测，成功回CLOSED</li>
 *   <li>HALF_OPEN探测失败：探测失败后重回OPEN</li>
 *   <li>HALF_OPEN防并发：只放行一个探测请求</li>
 *   <li>成功重置计数：成功后失败计数清零</li>
 *   <li>端到端路由：A持续失败熔断后所有请求路由到B</li>
 * </ol>
 */
class CircuitBreakerEmbeddingModelTest {

    private static final int THRESHOLD = 2;
    private static final long OPEN_DURATION_MS = 200;

    private EmbeddingProperties properties;
    private EmbeddingCircuitBreaker circuitBreaker;

    @BeforeEach
    void setUp() {
        EmbeddingProperties.Selection selection = new EmbeddingProperties.Selection();
        selection.setFailureThreshold(THRESHOLD);
        selection.setOpenDurationMs(OPEN_DURATION_MS);
        properties = new EmbeddingProperties();
        properties.setSelection(selection);
        circuitBreaker = new EmbeddingCircuitBreaker(properties);
    }

    // ──────────────────── Mock 工厂方法 ────────────────────

    /** 始终成功，返回指定维度零向量 */
    private EmbeddingModel successModel(int dim) {
        return new EmbeddingModel() {
            @Override
            public EmbeddingResponse call(EmbeddingRequest request) {
                float[] vector = new float[dim];
                return new EmbeddingResponse(List.of(new Embedding(vector, 0, EmbeddingResultMetadata.EMPTY)));
            }
            @Override
            public float[] embed(org.springframework.ai.document.Document document) {
                return new float[dim];
            }
        };
    }

    /** 始终抛异常 */
    private EmbeddingModel failModel(String msg) {
        return new EmbeddingModel() {
            @Override
            public EmbeddingResponse call(EmbeddingRequest request) {
                throw new RuntimeException(msg);
            }
            @Override
            public float[] embed(org.springframework.ai.document.Document document) {
                throw new RuntimeException(msg);
            }
        };
    }

    /** 前 failTimes 次失败，之后成功 */
    private EmbeddingModel failThenSuccessModel(int failTimes, int dim) {
        AtomicInteger count = new AtomicInteger(0);
        return new EmbeddingModel() {
            @Override
            public EmbeddingResponse call(EmbeddingRequest request) {
                if (count.incrementAndGet() <= failTimes) {
                    throw new RuntimeException("模拟第 " + count.get() + " 次失败");
                }
                float[] v = new float[dim];
                return new EmbeddingResponse(List.of(new Embedding(v, 0, EmbeddingResultMetadata.EMPTY)));
            }
            @Override
            public float[] embed(org.springframework.ai.document.Document document) {
                return new float[dim];
            }
        };
    }

    /**
     * 构建测试用 Entry：provider 用特殊值 "mock"，delegate.call() 会被直接调用。
     * 为此需要在 EmbeddingModelEntry.call() 里支持 "mock" provider，
     * 这里换一个思路：把 mock model 包装成能直接转发的 delegate，
     * 并用 "openai" provider（OpenAiEmbeddingOptions 无副作用，mock delegate 会忽略 options）。
     */
    private EmbeddingModelFactory.EmbeddingModelEntry entry(String id, EmbeddingModel model) {
        return new EmbeddingModelFactory.EmbeddingModelEntry(id, "mock-model", "openai", model);
    }

    private CircuitBreakerEmbeddingModel buildModel(List<EmbeddingModelFactory.EmbeddingModelEntry> entries) {
        return new CircuitBreakerEmbeddingModel(new EmbeddingModelFactory(entries), circuitBreaker);
    }

    private EmbeddingRequest dummyRequest() {
        return new EmbeddingRequest(List.of("测试文本"),
                org.springframework.ai.embedding.EmbeddingOptionsBuilder.builder().build());
    }

    // ──────────────────── 测试用例 ────────────────────

    @Test
    @DisplayName("场景1：正常路由 - 请求打到唯一候选并返回向量")
    void testNormalRouting() {
        var model = buildModel(List.of(entry("model-a", successModel(1024))));

        EmbeddingResponse response = model.call(dummyRequest());

        assertNotNull(response);
        assertEquals(1024, response.getResults().get(0).getOutput().length);
        System.out.println("[场景1] 正常路由成功，向量维度=" + response.getResults().get(0).getOutput().length + " ✓");
    }

    @Test
    @DisplayName("场景2：自动切换 - 候选A失败，自动切换到候选B成功")
    void testFailoverToNextCandidate() {
        AtomicInteger bCallCount = new AtomicInteger(0);
        EmbeddingModel modelB = new EmbeddingModel() {
            @Override
            public EmbeddingResponse call(EmbeddingRequest request) {
                bCallCount.incrementAndGet();
                float[] v = new float[512];
                return new EmbeddingResponse(List.of(new Embedding(v, 0, EmbeddingResultMetadata.EMPTY)));
            }
            @Override
            public float[] embed(org.springframework.ai.document.Document d) { return new float[512]; }
        };

        var model = buildModel(List.of(
                entry("model-a", failModel("A故障")),
                entry("model-b", modelB)
        ));

        EmbeddingResponse response = model.call(dummyRequest());

        assertNotNull(response);
        assertEquals(1, bCallCount.get(), "候选B应被调用一次");
        System.out.println("[场景2] 候选A失败，切换到候选B成功，B调用次数=" + bCallCount.get() + " ✓");
    }

    @Test
    @DisplayName("场景3：触发熔断 - 连续失败达到阈值后进入OPEN，拒绝调用")
    void testCircuitBreakerTriggered() {
        circuitBreaker.markFailure("model-x");
        assertTrue(circuitBreaker.allowCall("model-x"), "第1次失败后仍应允许");

        circuitBreaker.markFailure("model-x");
        assertFalse(circuitBreaker.allowCall("model-x"), "达到阈值后应进入OPEN，拒绝调用");

        System.out.println("[场景3] 连续失败 " + THRESHOLD + " 次触发熔断，OPEN状态拒绝调用 ✓");
    }

    @Test
    @DisplayName("场景4：全部熔断 - 所有候选OPEN时抛出异常")
    void testAllCandidatesOpenThrowsException() {
        for (int i = 0; i < THRESHOLD; i++) {
            circuitBreaker.markFailure("model-a");
            circuitBreaker.markFailure("model-b");
        }

        var model = buildModel(List.of(
                entry("model-a", successModel(1024)),
                entry("model-b", successModel(1024))
        ));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> model.call(dummyRequest()));
        assertTrue(ex.getMessage().contains("所有 Embedding 候选模型均不可用"));
        System.out.println("[场景4] 所有候选熔断后抛出异常: " + ex.getMessage() + " ✓");
    }

    @Test
    @DisplayName("场景5：HALF_OPEN探测恢复 - 超时后允许探测，成功回CLOSED")
    void testHalfOpenRecovery() throws InterruptedException {
        circuitBreaker.markFailure("model-a");
        circuitBreaker.markFailure("model-a");
        assertFalse(circuitBreaker.allowCall("model-a"), "OPEN状态应拒绝");

        Thread.sleep(OPEN_DURATION_MS + 50);

        assertTrue(circuitBreaker.allowCall("model-a"), "超时后应转为HALF_OPEN，放行探测");

        circuitBreaker.markSuccess("model-a");
        assertTrue(circuitBreaker.allowCall("model-a"), "探测成功后应回到CLOSED");

        System.out.println("[场景5] HALF_OPEN探测成功，状态恢复为CLOSED ✓");
    }

    @Test
    @DisplayName("场景6：HALF_OPEN探测失败 - 探测失败后重回OPEN")
    void testHalfOpenProbeFailed() throws InterruptedException {
        circuitBreaker.markFailure("model-a");
        circuitBreaker.markFailure("model-a");

        Thread.sleep(OPEN_DURATION_MS + 50);
        assertTrue(circuitBreaker.allowCall("model-a"), "应转为HALF_OPEN");

        circuitBreaker.markFailure("model-a");
        assertFalse(circuitBreaker.allowCall("model-a"), "探测失败后应重回OPEN");

        System.out.println("[场景6] HALF_OPEN探测失败，重回OPEN ✓");
    }

    @Test
    @DisplayName("场景7：HALF_OPEN防并发 - 只放行一个探测，其余并发拒绝")
    void testHalfOpenOnlyOneProbe() throws InterruptedException {
        circuitBreaker.markFailure("model-a");
        circuitBreaker.markFailure("model-a");

        Thread.sleep(OPEN_DURATION_MS + 50);

        assertTrue(circuitBreaker.allowCall("model-a"), "第一个探测应被放行");
        assertFalse(circuitBreaker.allowCall("model-a"), "并发第二个探测应被拒绝");

        System.out.println("[场景7] HALF_OPEN只放行一个探测，并发被拒绝 ✓");
    }

    @Test
    @DisplayName("场景8：成功重置计数 - 成功后失败计数清零，需重新积累才熔断")
    void testSuccessResetsFailureCount() {
        circuitBreaker.markFailure("model-a");
        assertTrue(circuitBreaker.allowCall("model-a"), "1次失败仍允许");

        circuitBreaker.markSuccess("model-a");  // 清零

        circuitBreaker.markFailure("model-a");
        assertTrue(circuitBreaker.allowCall("model-a"), "清零后重新积累，1次失败仍允许");

        circuitBreaker.markFailure("model-a");
        assertFalse(circuitBreaker.allowCall("model-a"), "重新积累到阈值才熔断");

        System.out.println("[场景8] 成功后失败计数清零，重新积累到阈值才熔断 ✓");
    }

    @Test
    @DisplayName("场景9：端到端 - A持续失败熔断后所有请求自动路由到B")
    void testEndToEndFailoverWithCircuitBreaker() {
        AtomicInteger bCallCount = new AtomicInteger(0);
        EmbeddingModel modelB = new EmbeddingModel() {
            @Override
            public EmbeddingResponse call(EmbeddingRequest request) {
                bCallCount.incrementAndGet();
                float[] v = new float[768];
                return new EmbeddingResponse(List.of(new Embedding(v, 0, EmbeddingResultMetadata.EMPTY)));
            }
            @Override
            public float[] embed(org.springframework.ai.document.Document d) { return new float[768]; }
        };

        var model = buildModel(List.of(
                entry("model-a", failModel("A不可用")),
                entry("model-b", modelB)
        ));

        // 第1次：A失败(计数=1)，切换到B成功
        assertNotNull(model.call(dummyRequest()));
        System.out.println("[场景9] 第1次: A失败(计数=1)，路由到B成功");

        // 第2次：A失败(计数=2=阈值)触发熔断，切换到B成功
        assertNotNull(model.call(dummyRequest()));
        System.out.println("[场景9] 第2次: A失败(计数=2)触发熔断，路由到B成功");

        // 第3次：A被熔断直接跳过，直接打到B
        assertNotNull(model.call(dummyRequest()));
        assertEquals(3, bCallCount.get(), "三次调用B都应被命中");
        System.out.println("[场景9] 第3次: A被熔断跳过，直接路由到B，B总调用=" + bCallCount.get() + " ✓");
    }
}
