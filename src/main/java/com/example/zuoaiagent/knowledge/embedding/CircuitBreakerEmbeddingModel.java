package com.example.zuoaiagent.knowledge.embedding;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

import java.util.List;

/**
 * 带熔断机制的 Embedding 模型包装器（实现 {@link EmbeddingModel} 接口）
 *
 * <p>通过 {@link EmbeddingModelFactory} 持有的 {@link java.util.Deque} 队列依次取候选，
 * 结合 {@link EmbeddingCircuitBreaker} 实现跨厂商（Ollama / OpenAI 兼容）的熔断切换：
 * <ul>
 *   <li>CLOSED → 正常调用</li>
 *   <li>OPEN → 跳过，切换下一候选</li>
 *   <li>HALF_OPEN → 放行一个探测请求</li>
 * </ul>
 *
 * <p>路由策略：每次调用最多轮询 {@code factory.size()} 次；成功后将 entry 放回队尾；
 * 失败后也放回队尾，切换到下一个候选继续尝试。
 *
 * <p>此包装器注册为 {@code @Bean}（见 {@link com.example.zuoaiagent.config.VectorStoreConfig}），
 * 作为 {@link org.springframework.ai.vectorstore.pgvector.PgVectorStore} 的 EmbeddingModel，
 * 使 {@code vectorStore.add()} 写入路径也受熔断保护。
 *
 * <p>参照 ragent {@code infra-ai} 模块的 {@code ModelRoutingExecutor} 设计。
 */
public class CircuitBreakerEmbeddingModel implements EmbeddingModel {

    private static final Logger log = LoggerFactory.getLogger(CircuitBreakerEmbeddingModel.class);

    private final EmbeddingModelFactory factory;
    private final EmbeddingCircuitBreaker circuitBreaker;

    public CircuitBreakerEmbeddingModel(EmbeddingModelFactory factory,
                                        EmbeddingCircuitBreaker circuitBreaker) {
        this.factory = factory;
        this.circuitBreaker = circuitBreaker;
    }

    /**
     * 执行带熔断的 Embedding 调用。
     *
     * <p>从工厂队列头部取候选，最多遍历 {@code factory.size()} 次：
     * 跳过被熔断的候选，成功后放回队尾并返回结果；失败后放回队尾并继续下一轮。
     * 全部失败则抛出 {@link RuntimeException}。
     *
     * @param request Embedding 请求（包含文本列表）
     * @return Embedding 响应（包含向量结果）
     */
    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        int total = factory.size();
        if (total == 0) {
            throw new RuntimeException("EmbeddingModelFactory 中没有可用候选，请检查 embedding.candidates 配置");
        }

        for (int attempt = 0; attempt < total; attempt++) {
            EmbeddingModelFactory.EmbeddingModelEntry entry = factory.poll();
            if (entry == null) {
                break;
            }

            String id = entry.id();

            // 熔断器检查
            if (!circuitBreaker.allowCall(id)) {
                log.debug("[熔断路由] 候选 {} 被熔断，跳过", id);
                factory.offerTail(entry);
                continue;
            }

            try {
                EmbeddingResponse response = entry.call(request);
                circuitBreaker.markSuccess(id);
                factory.offerTail(entry);
                log.debug("[熔断路由] 候选 {} 调用成功（provider={}）", id, entry.provider());
                return response;

            } catch (Exception e) {
                circuitBreaker.markFailure(id);
                factory.offerTail(entry);
                log.warn("[熔断路由] 候选 {} 调用失败（provider={}），切换下一候选。原因: {}",
                        id, entry.provider(), e.getMessage());
            }
        }

        throw new RuntimeException("所有 Embedding 候选模型均不可用");
    }

    /**
     * 对 {@link Document} 进行向量化。
     *
     * <p>使用文档的格式化内容文本，通过路由+熔断逻辑完成 Embedding。
     *
     * @param document Spring AI 文档对象
     * @return 浮点向量
     */
    @Override
    public float[] embed(Document document) {
        EmbeddingResponse response = call(new EmbeddingRequest(
                List.of(document.getText()),
                org.springframework.ai.embedding.EmbeddingOptionsBuilder.builder().build()
        ));
        return response.getResults().get(0).getOutput();
    }
}
