package com.example.zuoaiagent.raglab.service.impl;

import com.example.zuoaiagent.raglab.entity.LlmModelConfigDO;
import com.example.zuoaiagent.raglab.service.LlmModelConfigService;
import com.example.zuoaiagent.raglab.service.ModelRouterService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 模型路由器服务实现
 * 缓存已创建的 ChatModel 实例，避免重复创建
 */
@Service
public class ModelRouterServiceImpl implements ModelRouterService {

    private static final Logger log = LoggerFactory.getLogger(ModelRouterServiceImpl.class);

    private final LlmModelConfigService configService;

    /** 缓存已创建的 ChatModel 实例 */
    private final Map<Long, ChatModel> chatModelCache = new ConcurrentHashMap<>();

    public ModelRouterServiceImpl(LlmModelConfigService configService) {
        this.configService = configService;
    }

    @Override
    public ChatModel resolveChatModel(Long modelConfigId) {
        return chatModelCache.computeIfAbsent(modelConfigId, this::createChatModel);
    }

    @Override
    public String getModelName(Long modelConfigId) {
        LlmModelConfigDO config = configService.getById(modelConfigId);
        if (config == null) {
            throw new IllegalArgumentException("模型配置不存在: " + modelConfigId);
        }
        return config.getModelName();
    }

    /**
     * 动态创建 ChatModel 实例（统一 OpenAI 协议）
     */
    private ChatModel createChatModel(Long modelConfigId) {
        LlmModelConfigDO config = configService.getById(modelConfigId);
        if (config == null) {
            throw new IllegalArgumentException("模型配置不存在: " + modelConfigId);
        }

        log.info("[ModelRouter] 创建 ChatModel: provider={}, model={}, baseUrl={}",
                config.getProvider(), config.getModelId(), config.getBaseUrl());

        String apiKey = configService.getDecryptedApiKey(modelConfigId);

        OpenAiApi openAiApi = OpenAiApi.builder()
                .baseUrl(config.getBaseUrl())
                .apiKey(apiKey)
                .build();

        return OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(OpenAiChatOptions.builder()
                        .model(config.getModelId())
                        .maxTokens(config.getMaxTokens())
                        .temperature(config.getTemperature())
                        .build())
                .build();
    }

    /**
     * 清除缓存（模型配置更新时调用）
     */
    public void evictCache(Long modelConfigId) {
        chatModelCache.remove(modelConfigId);
    }

    /**
     * 清除所有缓存
     */
    public void evictAllCache() {
        chatModelCache.clear();
    }
}
