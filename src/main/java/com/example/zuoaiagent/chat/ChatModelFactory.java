package com.example.zuoaiagent.chat;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;

/**
 * Chat 模型工厂
 *
 * <p>按 {@code chat.candidates} 优先级初始化 {@link ArrayDeque} 队列，
 * 每个条目持有候选 id、模型名、provider 以及对应的底层 {@link ChatModel} 实例。
 *
 * <p>支持的 provider：
 * <ul>
 *   <li>{@code "dashscope"} — 阿里云 DashScope，使用 {@link DashScopeChatOptions} 覆盖模型名</li>
 *   <li>{@code "openai"} — OpenAI 兼容接口（如硅基流动），使用 {@link OpenAiChatOptions}</li>
 * </ul>
 */
@Component
public class ChatModelFactory {

    private static final Logger log = LoggerFactory.getLogger(ChatModelFactory.class);

    /**
     * 候选 Chat 模型条目，持有 id、模型名、provider 及可调用的委托。
     */
    public record ChatModelEntry(String id, String modelName, String provider, ChatModel delegate) {

        /**
         * 构建覆盖模型名的 {@link ChatOptions}，用于 {@code ChatClientRequestSpec.options()} 调用。
         */
        public ChatOptions buildOptions() {
            return switch (provider) {
                case "dashscope" -> DashScopeChatOptions.builder().withModel(modelName).build();
                case "openai" -> OpenAiChatOptions.builder().model(modelName).build();
                default -> throw new IllegalStateException("未知 provider: " + provider);
            };
        }
    }

    private final Deque<ChatModelEntry> queue;

    /** 测试专用构造器：直接传入已构建好的 Entry 列表，不依赖 Spring 容器 */
    public ChatModelFactory(List<ChatModelEntry> entries) {
        this.queue = new ArrayDeque<>(entries);
    }

    @Autowired
    public ChatModelFactory(ChatModelProperties properties,
                            DashScopeChatModel dashScopeChatModel,
                            OpenAiChatModel openAiChatModel) {
        this.queue = new ArrayDeque<>();

        if (properties.getCandidates() == null || properties.getCandidates().isEmpty()) {
            log.warn("[ChatModelFactory] 未配置任何 Chat 候选，chat.candidates 为空");
            return;
        }

        properties.getCandidates().stream()
                .filter(c -> !Boolean.FALSE.equals(c.getEnabled()))
                .sorted(Comparator.comparingInt(c -> c.getPriority() == null ? Integer.MAX_VALUE : c.getPriority()))
                .forEach(candidate -> {
                    String provider = candidate.getProvider();
                    ChatModel delegate = resolveDelegate(provider, dashScopeChatModel, openAiChatModel);
                    queue.addLast(new ChatModelEntry(
                            candidate.getId(),
                            candidate.getModel(),
                            provider,
                            delegate
                    ));
                    log.info("[ChatModelFactory] 注册候选: id={}, model={}, provider={}",
                            candidate.getId(), candidate.getModel(), provider);
                });
    }

    /** 从队头取出一个候选条目。 */
    public synchronized ChatModelEntry poll() {
        return queue.pollFirst();
    }

    /** 将候选条目放回队尾（降级/重排）。 */
    public synchronized void offerTail(ChatModelEntry entry) {
        if (entry != null) {
            queue.addLast(entry);
        }
    }

    /** 返回当前队列中候选数量。 */
    public synchronized int size() {
        return queue.size();
    }

    private ChatModel resolveDelegate(String provider,
                                      DashScopeChatModel dashscope,
                                      OpenAiChatModel openai) {
        if (provider == null) {
            throw new IllegalArgumentException("候选模型未配置 provider 字段");
        }
        return switch (provider) {
            case "dashscope" -> dashscope;
            case "openai" -> openai;
            default -> throw new IllegalArgumentException("不支持的 provider: " + provider);
        };
    }
}
