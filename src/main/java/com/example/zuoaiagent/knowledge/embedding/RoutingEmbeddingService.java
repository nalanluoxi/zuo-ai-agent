package com.example.zuoaiagent.knowledge.embedding;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

/**
 * 带熔断机制的 Embedding 路由服务（独立服务 Bean）
 *
 * <p>与 {@link CircuitBreakerEmbeddingModel} 不同，本类作为独立 Spring Bean 暴露
 * {@link #embed(String)} 方法，供需要直接获取原始向量的业务代码调用（如后续扩展的
 * 自定义向量写入逻辑）。
 *
 * <p>路由逻辑：按 {@code priority} 升序遍历候选列表，通过 {@link OllamaOptions}
 * 动态覆盖模型名，结合熔断器实现自动故障切换。
 *
 * <p>参照 ragent {@code infra-ai} 模块的 {@code RoutingEmbeddingService}。
 */
@Service
public class RoutingEmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(RoutingEmbeddingService.class);

    /**
     * 底层 Ollama Embedding 模型（Spring AI 自动装配）。
     * 通过 {@link OllamaOptions} 在运行时切换具体模型名，实现多候选路由。
     */
    private final OllamaEmbeddingModel ollamaEmbeddingModel;

    private final EmbeddingProperties properties;
    private final EmbeddingCircuitBreaker circuitBreaker;

    public RoutingEmbeddingService(OllamaEmbeddingModel ollamaEmbeddingModel,
                                   EmbeddingProperties properties,
                                   EmbeddingCircuitBreaker circuitBreaker) {
        this.ollamaEmbeddingModel = ollamaEmbeddingModel;
        this.properties = properties;
        this.circuitBreaker = circuitBreaker;
    }

    /**
     * 对单个文本块进行向量化，支持多候选模型自动故障切换。
     *
     * @param text 待向量化的文本（非空）
     * @return 浮点向量（bge-m3 维度为 1024）
     * @throws RuntimeException 所有候选模型均不可用时抛出
     */
    public float[] embed(String text) {
        List<EmbeddingModelCandidate> sorted = getSortedCandidates();

        for (EmbeddingModelCandidate candidate : sorted) {
            // 跳过禁用的候选
            if (Boolean.FALSE.equals(candidate.getEnabled())) {
                continue;
            }

            String id = candidate.getId();

            // 熔断器检查：OPEN 状态且未到恢复时间则跳过
            if (!circuitBreaker.allowCall(id)) {
                log.debug("[路由] 候选模型 {} 被熔断，跳过", id);
                continue;
            }

            try {
                // 动态指定模型名，通过 OllamaOptions 覆盖 application.yaml 中的默认模型
                EmbeddingRequest request = new EmbeddingRequest(
                        List.of(text),
                        OllamaOptions.builder()
                                .model(candidate.getModel())
                                .build()
                );
                EmbeddingResponse response = ollamaEmbeddingModel.call(request);
                float[] vector = response.getResults().get(0).getOutput();

                // 成功：重置熔断器
                circuitBreaker.markSuccess(id);
                log.debug("[路由] 候选模型 {} 向量化成功，维度={}", id, vector.length);
                return vector;

            } catch (Exception e) {
                // 失败：更新熔断器，尝试下一候选
                circuitBreaker.markFailure(id);
                log.warn("[路由] 候选模型 {} 向量化失败，切换下一候选。原因: {}", id, e.getMessage());
            }
        }

        throw new RuntimeException("所有 Embedding 候选模型均不可用，文本向量化失败");
    }

    /** 返回按 priority 升序排列、已启用的候选列表 */
    private List<EmbeddingModelCandidate> getSortedCandidates() {
        if (properties.getCandidates() == null || properties.getCandidates().isEmpty()) {
            throw new RuntimeException("未配置任何 Embedding 候选模型，请检查 embedding.candidates 配置");
        }
        return properties.getCandidates().stream()
                .filter(c -> !Boolean.FALSE.equals(c.getEnabled()))
                .sorted(Comparator.comparingInt(c -> c.getPriority() == null ? Integer.MAX_VALUE : c.getPriority()))
                .toList();
    }
}
