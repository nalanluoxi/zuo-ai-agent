package com.example.zuoaiagent.demo;

import com.example.zuoaiagent.knowledge.embedding.CircuitBreakerEmbeddingModel;
import com.example.zuoaiagent.knowledge.embedding.EmbeddingCircuitBreaker;
import com.example.zuoaiagent.knowledge.embedding.EmbeddingModelFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 熔断路由集成测试 —— 使用真实模型（OpenAI/SiliconFlow）
 *
 * <p>启动 Spring 容器，注入真实的 {@link CircuitBreakerEmbeddingModel}，
 * 验证实际 API 调用下的路由与熔断行为。
 *
 * <p>前提：{@code application-local.yaml} 中 {@code spring.ai.openai.api-key} 填写有效 key。
 */
@SpringBootTest
@ActiveProfiles("local")
class CircuitBreakerEmbeddingIntegrationTest {

    @Autowired
    private CircuitBreakerEmbeddingModel circuitBreakerEmbeddingModel;

    @Autowired
    private EmbeddingModelFactory factory;

    @Autowired
    private EmbeddingCircuitBreaker circuitBreaker;

    private EmbeddingRequest request(String text) {
        return new EmbeddingRequest(List.of(text),
                org.springframework.ai.embedding.EmbeddingOptionsBuilder.builder().build());
    }

    @Test
    @DisplayName("集成1：真实调用 - 正常获取向量")
    void testRealEmbedding() {
        EmbeddingResponse response = circuitBreakerEmbeddingModel.call(request("什么是向量数据库？"));

        assertNotNull(response);
        float[] vector = response.getResults().get(0).getOutput();
        assertTrue(vector.length > 0, "向量维度应大于0");

        System.out.println("向量维度: " + vector.length);
        System.out.printf("向量前5维: [%.4f, %.4f, %.4f, %.4f, %.4f]%n",
                vector[0], vector[1], vector[2], vector[3], vector[4]);
    }

    @Test
    @DisplayName("集成2：熔断后自动恢复 - 手动触发熔断，等待恢复后真实调用成功")
    void testCircuitBreakerRecovery() throws InterruptedException {
        // 查找当前队列中第一个可用候选的 id
        EmbeddingModelFactory.EmbeddingModelEntry entry = factory.poll();
        assertNotNull(entry, "工厂队列不能为空");
        String candidateId = entry.id();
        factory.offerTail(entry); // 放回去

        System.out.println("目标候选: " + candidateId);

        // 手动触发熔断（连续失败2次）
        circuitBreaker.markFailure(candidateId);
        circuitBreaker.markFailure(candidateId);
        assertFalse(circuitBreaker.allowCall(candidateId), "应进入OPEN状态");
        System.out.println("已手动触发熔断，候选 " + candidateId + " 进入OPEN状态");

        // 等待熔断超时（openDurationMs 默认30s，这里为了测试用短时间需在 yaml 里调小，
        // 或直接通过 markSuccess 手动恢复）
        circuitBreaker.markSuccess(candidateId);
        assertTrue(circuitBreaker.allowCall(candidateId), "手动恢复后应回到CLOSED");
        System.out.println("手动恢复后候选 " + candidateId + " 回到CLOSED状态");

        // 真实调用应正常
        EmbeddingResponse response = circuitBreakerEmbeddingModel.call(request("熔断恢复后的测试文本"));
        assertNotNull(response);
        System.out.println("恢复后真实调用成功，向量维度=" + response.getResults().get(0).getOutput().length);
    }

    @Test
    @DisplayName("集成3：连续调用 - 验证队列轮转，同一候选被反复使用")
    void testContinuousCalls() {
        String[] texts = {"向量检索", "知识库问答", "文档入库", "语义相似度", "embedding模型"};

        for (int i = 0; i < texts.length; i++) {
            EmbeddingResponse response = circuitBreakerEmbeddingModel.call(request(texts[i]));
            assertNotNull(response);
            float[] vector = response.getResults().get(0).getOutput();
            System.out.printf("第%d次调用成功: text=\"%s\", 维度=%d, 首维=%.4f%n",
                    i + 1, texts[i], vector.length, vector[0]);
        }
    }
}
