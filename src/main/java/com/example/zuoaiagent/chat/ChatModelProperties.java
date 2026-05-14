package com.example.zuoaiagent.chat;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Chat 模型相关配置属性，绑定 {@code application.yaml} 中的 {@code chat} 前缀。
 *
 * <p>示例配置：
 * <pre>{@code
 * chat:
 *   selection:
 *     failure-threshold: 2
 *     open-duration-ms: 30000
 *   candidates:
 *     - id: dashscope-qwen
 *       provider: dashscope
 *       model: qwen-plus
 *       priority: 1
 *       enabled: true
 *     - id: siliconflow-deepseek
 *       provider: openai
 *       model: deepseek-ai/DeepSeek-V3
 *       priority: 2
 *       enabled: true
 * }</pre>
 */
@Component
@ConfigurationProperties(prefix = "chat")
public class ChatModelProperties {

    private List<ChatModelCandidate> candidates;
    private Selection selection = new Selection();

    public List<ChatModelCandidate> getCandidates() { return candidates; }
    public void setCandidates(List<ChatModelCandidate> candidates) { this.candidates = candidates; }

    public Selection getSelection() { return selection; }
    public void setSelection(Selection selection) { this.selection = selection; }

    public static class Selection {

        /** 连续失败多少次后触发熔断（进入 OPEN 状态）。默认 2 次。 */
        private int failureThreshold = 2;

        /** 熔断器处于 OPEN 状态的持续时间（毫秒），超过后转为 HALF_OPEN。默认 30000 ms。 */
        private long openDurationMs = 30000;

        public int getFailureThreshold() { return failureThreshold; }
        public void setFailureThreshold(int failureThreshold) { this.failureThreshold = failureThreshold; }

        public long getOpenDurationMs() { return openDurationMs; }
        public void setOpenDurationMs(long openDurationMs) { this.openDurationMs = openDurationMs; }
    }
}
