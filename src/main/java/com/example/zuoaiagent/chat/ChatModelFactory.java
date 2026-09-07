package com.example.zuoaiagent.chat;

import com.example.zuoaiagent.raglab.entity.LlmModelConfigDO;
import com.example.zuoaiagent.raglab.service.LlmModelConfigService;
import com.example.zuoaiagent.raglab.service.ModelRouterService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.List;

/**
 * Chat 模型工厂
 *
 * <p>混合模式：本地 Ollama 模型（qwen2.5:7b）写死在 YAML，始终可用且优先级最高；
 * 远程模型从数据库 t_llm_model_config 表动态加载，受灰度规则过滤。
 * 不再依赖 YAML 配置和环境变量加载远程模型。
 */
@Component
public class ChatModelFactory {

    private static final Logger log = LoggerFactory.getLogger(ChatModelFactory.class);

    public record ChatModelEntry(String id, String modelName, String provider, ChatModel delegate) {

        public ChatOptions buildOptions() {
            return OpenAiChatOptions.builder().model(modelName).build();
        }
    }

    private volatile List<ChatModelEntry> candidates;
    private final OllamaChatModel ollamaChatModel;
    private final LlmModelConfigService configService;
    private final ModelRouterService modelRouterService;

    @org.springframework.beans.factory.annotation.Autowired
    public ChatModelFactory(LlmModelConfigService configService,
                            ModelRouterService modelRouterService,
                            @org.springframework.beans.factory.annotation.Qualifier("ollamaChatModel") OllamaChatModel ollamaChatModel) {
        this.configService = configService;
        this.modelRouterService = modelRouterService;
        this.ollamaChatModel = ollamaChatModel;

        List<ChatModelEntry> list = new ArrayList<>();

        // 1. 本地 Ollama 模型（写死，始终排在第一位，优先级最高）
        list.add(new ChatModelEntry(
                "local-ollama",
                "qwen2.5:7b",
                "ollama",
                ollamaChatModel
        ));
        log.info("[ChatModelFactory] 注册本地模型: qwen2.5:7b (ollama) — 优先级最高");

        // 2. 从数据库加载激活的远程模型配置
        try {
            List<LlmModelConfigDO> activeConfigs = configService.listActive();

            if (activeConfigs == null || activeConfigs.isEmpty()) {
                log.warn("[ChatModelFactory] 数据库中没有激活的模型配置");
            } else {
                // 按 ID 排序（ID 越小优先级越高）
                activeConfigs.stream()
                        .sorted(Comparator.comparing(LlmModelConfigDO::getId))
                        .forEach(config -> {
                            try {
                                ChatModel delegate = modelRouterService.resolveChatModel(config.getId());
                                list.add(new ChatModelEntry(
                                        config.getId().toString(),
                                        config.getModelId(),
                                        config.getProvider(),
                                        delegate
                                ));
                                log.info("[ChatModelFactory] 注册远程模型: id={}, model={}, provider={}",
                                        config.getId(), config.getModelId(), config.getProvider());
                            } catch (Exception e) {
                                log.error("[ChatModelFactory] 创建远程模型实例失败: id={}, model={}: {}",
                                        config.getId(), config.getModelId(), e.getMessage());
                            }
                        });
            }
        } catch (Exception e) {
            log.error("[ChatModelFactory] 加载远程模型配置失败: {}", e.getMessage(), e);
        }

        this.candidates = Collections.unmodifiableList(list);
    }

    public List<ChatModelEntry> getCandidates() {
        return candidates;
    }

    public int size() {
        return candidates.size();
    }

    /**
     * 重新加载模型列表：先加载本地 Ollama 模型，再加载数据库中激活的远程模型配置。
     * 使用 synchronized 保证并发安全，volatile 字段保证可见性。
     */
    public synchronized void reload() {
        log.info("[ChatModelFactory] 开始重新加载模型列表...");
        List<ChatModelEntry> newList = new ArrayList<>();

        // 1. 本地 Ollama 模型（写死，始终排在第一位）
        newList.add(new ChatModelEntry(
                "local-ollama",
                "qwen2.5:7b",
                "ollama",
                ollamaChatModel
        ));

        // 2. 从数据库重新加载激活的远程模型配置
        try {
            List<LlmModelConfigDO> activeConfigs = configService.listActive();
            if (activeConfigs != null && !activeConfigs.isEmpty()) {
                activeConfigs.stream()
                        .sorted(Comparator.comparing(LlmModelConfigDO::getId))
                        .forEach(config -> {
                            try {
                                ChatModel delegate = modelRouterService.resolveChatModel(config.getId());
                                newList.add(new ChatModelEntry(
                                        config.getId().toString(),
                                        config.getModelId(),
                                        config.getProvider(),
                                        delegate
                                ));
                                log.info("[ChatModelFactory] 重新加载远程模型: id={}, model={}",
                                        config.getId(), config.getModelId());
                            } catch (Exception e) {
                                log.error("[ChatModelFactory] 重新加载远程模型失败: id={}: {}",
                                        config.getId(), e.getMessage());
                            }
                        });
            }
        } catch (Exception e) {
            log.error("[ChatModelFactory] 重新加载远程模型配置失败: {}", e.getMessage(), e);
        }

        // 3. 原子性替换（volatile 保证可见性）
        this.candidates = Collections.unmodifiableList(newList);
        log.info("[ChatModelFactory] 模型列表重新加载完成，共 {} 个模型", newList.size());
    }

    /**
     * 提供默认的 ChatModel Bean（取第一个候选，即本地模型）
     * 用于 ChatClientConfig 等需要注入 ChatModel 的地方
     */
    @Bean
    @Primary
    public ChatModel defaultChatModel() {
        if (candidates.isEmpty()) {
            throw new IllegalStateException("没有可用的模型配置，请检查 t_llm_model_config 表是否有 is_active=1 的记录");
        }
        return candidates.get(0).delegate();
    }
}