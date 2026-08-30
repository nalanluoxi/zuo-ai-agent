package com.example.zuoaiagent.chat;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.List;

@Component
public class ChatModelFactory {

    private static final Logger log = LoggerFactory.getLogger(ChatModelFactory.class);

    public record ChatModelEntry(String id, String modelName, String provider, ChatModel delegate) {

        public ChatOptions buildOptions() {
            return switch (provider) {
                case "dashscope" -> DashScopeChatOptions.builder().withModel(modelName).build();
                case "openai" -> OpenAiChatOptions.builder().model(modelName).build();
                case "ollama" -> OllamaOptions.builder().model(modelName).build();
                default -> throw new IllegalStateException("未知 provider: " + provider);
            };
        }
    }

    private final List<ChatModelEntry> candidates;

    public ChatModelFactory(List<ChatModelEntry> entries) {
        this.candidates = Collections.unmodifiableList(new ArrayList<>(entries));
    }

    @Autowired
    public ChatModelFactory(ChatModelProperties properties,
                            DashScopeChatModel dashScopeChatModel,
                            OpenAiChatModel openAiChatModel,
                            OllamaChatModel ollamaChatModel) {
        List<ChatModelEntry> list = new ArrayList<>();

        if (properties.getCandidates() == null || properties.getCandidates().isEmpty()) {
            log.warn("[ChatModelFactory] 未配置任何 Chat 候选，chat.candidates 为空");
        } else {
            properties.getCandidates().stream()
                    .filter(c -> !Boolean.FALSE.equals(c.getEnabled()))
                    .sorted(Comparator.comparingInt(c -> c.getPriority() == null ? Integer.MAX_VALUE : c.getPriority()))
                    .forEach(candidate -> {
                        String provider = candidate.getProvider();
                        ChatModel delegate = resolveDelegate(provider, dashScopeChatModel, openAiChatModel, ollamaChatModel);
                        list.add(new ChatModelEntry(
                                candidate.getId(),
                                candidate.getModel(),
                                provider,
                                delegate
                        ));
                        log.info("[ChatModelFactory] 注册候选: id={}, model={}, provider={}",
                                candidate.getId(), candidate.getModel(), provider);
                    });
        }
        this.candidates = Collections.unmodifiableList(list);
    }

    public List<ChatModelEntry> getCandidates() {
        return candidates;
    }

    public int size() {
        return candidates.size();
    }

    private ChatModel resolveDelegate(String provider,
                                      DashScopeChatModel dashscope,
                                      OpenAiChatModel openai,
                                      OllamaChatModel ollama) {
        if (provider == null) {
            throw new IllegalArgumentException("候选模型未配置 provider 字段");
        }
        return switch (provider) {
            case "dashscope" -> dashscope;
            case "openai" -> openai;
            case "ollama" -> ollama;
            default -> throw new IllegalArgumentException("不支持的 provider: " + provider);
        };
    }
}