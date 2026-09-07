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

    private final List<ChatModelEntry> candidates;

    public ChatModelFactory(List<ChatModelEntry> entries) {
        this.candidates = Collections.unmodifiableList(new ArrayList<>(entries));
    }

    /**
     * 构造 ChatModelFactory：本地模型优先 + 数据库远程模型追加
     *
     * @param configService      模型配置服务（读取数据库）
     * @param modelRouterService 模型路由器（创建 ChatModel 实例）
     * @param ollamaChatModel    本地 Ollama 模型（写死，始终可用）
     */
    @org.springframework.beans.factory.annotation.Autowired
    public ChatModelFactory(LlmModelConfigService configService,
                            ModelRouterService modelRouterService,
                            @org.springframework.beans.factory.annotation.Qualifier("ollamaChatModel") OllamaChatModel ollamaChatModel) {
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