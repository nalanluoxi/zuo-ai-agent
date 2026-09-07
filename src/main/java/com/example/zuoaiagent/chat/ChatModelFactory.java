package com.example.zuoaiagent.chat;

import com.example.zuoaiagent.raglab.entity.LlmModelConfigDO;
import com.example.zuoaiagent.raglab.service.LlmModelConfigService;
import com.example.zuoaiagent.raglab.service.ModelRouterService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
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
 * <p>从数据库 t_llm_model_config 表读取激活的模型配置，
 * 通过 ModelRouterService 动态创建 ChatModel 实例。
 * 不再依赖 YAML 配置和环境变量。
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
     * 从数据库加载激活的模型配置，动态创建 ChatModel 实例
     */
    @org.springframework.beans.factory.annotation.Autowired
    public ChatModelFactory(LlmModelConfigService configService, ModelRouterService modelRouterService) {
        List<ChatModelEntry> list = new ArrayList<>();

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
                                log.info("[ChatModelFactory] 注册候选: id={}, model={}, provider={}",
                                        config.getId(), config.getModelId(), config.getProvider());
                            } catch (Exception e) {
                                log.error("[ChatModelFactory] 创建模型实例失败: id={}, model={}: {}",
                                        config.getId(), config.getModelId(), e.getMessage());
                            }
                        });
            }
        } catch (Exception e) {
            log.error("[ChatModelFactory] 加载模型配置失败: {}", e.getMessage(), e);
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
     * 提供默认的 ChatModel Bean（取第一个候选）
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