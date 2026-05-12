package com.example.zuoaiagent.knowledge.embedding;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Embedding 相关配置属性，绑定 {@code application.yaml} 中的 {@code embedding} 前缀。
 *
 * <p>示例配置：
 * <pre>{@code
 * embedding:
 *   selection:
 *     failure-threshold: 2
 *     open-duration-ms: 30000
 *   candidates:
 *     - id: siliconflow-bce
 *       model: bce-embedding-base_v1
 *       priority: 1
 *       enabled: true
 *     - id: siliconflow-bge-m3
 *       model: BAAI/bge-m3
 *       priority: 2
 *       enabled: true
 * }</pre>
 *
 * <p>参照 ragent {@code infra-ai} 模块的模型选择配置设计。
 */
@Component
@ConfigurationProperties(prefix = "embedding")
public class EmbeddingProperties {

    /**
     * 按优先级排列的 Embedding 模型候选列表。
     * {@link RoutingEmbeddingService} 按 {@code priority} 升序尝试。
     */
    private List<EmbeddingModelCandidate> candidates;

    /**
     * 熔断器选项，控制故障触发与恢复行为。
     */
    private Selection selection = new Selection();

    public List<EmbeddingModelCandidate> getCandidates() {
        return candidates;
    }

    public void setCandidates(List<EmbeddingModelCandidate> candidates) {
        this.candidates = candidates;
    }

    public Selection getSelection() {
        return selection;
    }

    public void setSelection(Selection selection) {
        this.selection = selection;
    }

    /**
     * 熔断器选项内部类。
     */
    public static class Selection {

        /**
         * 连续失败多少次后触发熔断（进入 OPEN 状态）。
         * 默认 2 次。
         */
        private int failureThreshold = 2;

        /**
         * 熔断器处于 OPEN 状态的持续时间（毫秒），超过后转为 HALF_OPEN 允许探测。
         * 默认 30000 ms（30 秒）。
         */
        private long openDurationMs = 30000;

        public int getFailureThreshold() {
            return failureThreshold;
        }

        public void setFailureThreshold(int failureThreshold) {
            this.failureThreshold = failureThreshold;
        }

        public long getOpenDurationMs() {
            return openDurationMs;
        }

        public void setOpenDurationMs(long openDurationMs) {
            this.openDurationMs = openDurationMs;
        }
    }
}
