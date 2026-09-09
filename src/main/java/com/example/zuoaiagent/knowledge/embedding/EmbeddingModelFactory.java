package com.example.zuoaiagent.knowledge.embedding;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.Optional;

/**
 * Embedding 模型工厂
 *
 * <p>按 {@code embedding.candidates} 优先级初始化 {@link ArrayDeque} 队列，
 * 每个条目持有候选 id、模型名、provider 以及对应的底层 {@link EmbeddingModel} 实例。
 *
 * <p>支持的 provider：
 * <ul>
 *   <li>{@code "ollama"} — 本地 Ollama，使用 {@link OllamaOptions} 覆盖模型名</li>
 *   <li>{@code "openai"} — OpenAI 兼容接口（如硅基流动），使用 {@link OpenAiEmbeddingOptions}</li>
 * </ul>
 *
 * <p>线程安全：{@code poll} / {@code offerTail} 均使用 {@code synchronized}，
 * 与 {@link EmbeddingCircuitBreaker} 保持一致，适合低并发的入库场景。
 */
@Component
public class EmbeddingModelFactory {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingModelFactory.class);

    /**
     * 候选 Embedding 模型条目，持有 id、模型名、provider 及可调用的委托。
     *
     * @param id        候选标识（与熔断器 key 对应）
     * @param modelName 实际传给 API 的模型名称
     * @param provider  服务商标识（{@code "ollama"} 或 {@code "openai"}）
     * @param delegate  底层 {@link EmbeddingModel} 实例（OllamaEmbeddingModel 或 OpenAiEmbeddingModel）
     */
    public record EmbeddingModelEntry(String id, String modelName, String provider, EmbeddingModel delegate) {

        /**
         * 执行本候选的 Embedding 调用，动态注入模型名 options。
         *
         * @param request 原始 EmbeddingRequest（instructions 将被复用）
         * @return Embedding 响应
         */
        public EmbeddingResponse call(EmbeddingRequest request) {
            EmbeddingRequest routed = switch (provider) {
                case "ollama" -> new EmbeddingRequest(
                        request.getInstructions(),
                        OllamaOptions.builder().model(modelName).build()
                );
                case "openai" -> new EmbeddingRequest(
                        request.getInstructions(),
                        OpenAiEmbeddingOptions.builder().model(modelName).build()
                );
                default -> throw new IllegalStateException("未知 provider: " + provider);
            };
            return delegate.call(routed);
        }
    }

    /** 按优先级排序的候选队列 */
    private final Deque<EmbeddingModelEntry> queue;

    /** 测试专用构造器：直接传入已构建好的 Entry 列表，不依赖 Spring 容器 */
    public EmbeddingModelFactory(java.util.List<EmbeddingModelEntry> entries) {
        this.queue = new ArrayDeque<>(entries);
    }

    @Autowired
    public EmbeddingModelFactory(EmbeddingProperties properties,
                                 @org.springframework.beans.factory.annotation.Qualifier("ollamaEmbeddingModel") OllamaEmbeddingModel ollamaEmbeddingModel,
                                 @Autowired(required = false) OpenAiEmbeddingModel openAiEmbeddingModel) {
        this.queue = new ArrayDeque<>();

        if (properties.getCandidates() == null || properties.getCandidates().isEmpty()) {
            log.warn("[EmbeddingModelFactory] 未配置任何 Embedding 候选，embedding.candidates 为空");
            return;
        }

        properties.getCandidates().stream()
                .filter(c -> !Boolean.FALSE.equals(c.getEnabled()))
                .sorted(Comparator.comparingInt(c -> c.getPriority() == null ? Integer.MAX_VALUE : c.getPriority()))
                .forEach(candidate -> {
                    String provider = candidate.getProvider();
                    try {
                        EmbeddingModel delegate = resolveDelegate(provider, ollamaEmbeddingModel, openAiEmbeddingModel);
                        queue.addLast(new EmbeddingModelEntry(
                                candidate.getId(),
                                candidate.getModel(),
                                provider,
                                delegate
                        ));
                        log.info("[EmbeddingModelFactory] 注册候选: id={}, model={}, provider={}",
                                candidate.getId(), candidate.getModel(), provider);
                    } catch (IllegalStateException e) {
                        log.warn("[EmbeddingModelFactory] 跳过候选 {}: {}", candidate.getId(), e.getMessage());
                    }
                });
    }

    /**
     * 从队头取出一个候选条目。
     *
     * @return 队头候选，若队列为空返回 {@code null}
     */
    public synchronized EmbeddingModelEntry poll() {
        return queue.pollFirst();
    }

    /**
     * 将候选条目放回队尾（降级/重排）。
     *
     * @param entry 要放回的条目
     */
    public synchronized void offerTail(EmbeddingModelEntry entry) {
        if (entry != null) {
            queue.addLast(entry);
        }
    }

    /**
     * 返回当前队列中候选数量。
     */
    public synchronized int size() {
        return queue.size();
    }

    /** 根据 provider 字符串返回对应的底层 EmbeddingModel */
    private EmbeddingModel resolveDelegate(String provider,
                                           OllamaEmbeddingModel ollama,
                                           OpenAiEmbeddingModel openai) {
        if (provider == null) {
            throw new IllegalArgumentException("候选模型未配置 provider 字段");
        }
        return switch (provider) {
            case "ollama" -> ollama;
            case "openai" -> {
                if (openai == null) {
                    throw new IllegalStateException("OpenAI Embedding 未启用，无法注册 openai 候选");
                }
                yield openai;
            }
            default -> throw new IllegalArgumentException("不支持的 provider: " + provider);
        };
    }
}
