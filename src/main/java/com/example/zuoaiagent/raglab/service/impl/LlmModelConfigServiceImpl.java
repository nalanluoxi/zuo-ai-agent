package com.example.zuoaiagent.raglab.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.zuoaiagent.raglab.entity.LlmModelConfigDO;
import com.example.zuoaiagent.raglab.mapper.LlmModelConfigMapper;
import com.example.zuoaiagent.raglab.service.LlmModelConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

/**
 * LLM 模型配置服务实现
 */
@Service
public class LlmModelConfigServiceImpl implements LlmModelConfigService {

    private static final Logger log = LoggerFactory.getLogger(LlmModelConfigServiceImpl.class);

    private final LlmModelConfigMapper configMapper;

    public LlmModelConfigServiceImpl(LlmModelConfigMapper configMapper) {
        this.configMapper = configMapper;
    }

    @Override
    public LlmModelConfigDO create(LlmModelConfigDO config) {
        config.setStatus("DRAFT");
        config.setCreateTime(new Date());
        config.setUpdateTime(new Date());
        // 自动规范化 baseUrl，去掉末尾的 /v1
        config.setBaseUrl(normalizeBaseUrl(config.getBaseUrl()));
        configMapper.insert(config);
        return config;
    }

    @Override
    public LlmModelConfigDO update(LlmModelConfigDO config) {
        config.setUpdateTime(new Date());
        // 自动规范化 baseUrl，去掉末尾的 /v1
        config.setBaseUrl(normalizeBaseUrl(config.getBaseUrl()));
        configMapper.updateById(config);
        return config;
    }

    @Override
    public LlmModelConfigDO getById(Long id) {
        return configMapper.selectById(id);
    }

    @Override
    public List<LlmModelConfigDO> listAll() {
        return configMapper.selectList(new LambdaQueryWrapper<LlmModelConfigDO>()
                .orderByDesc(LlmModelConfigDO::getCreateTime));
    }

    @Override
    public List<LlmModelConfigDO> listActive() {
        return configMapper.selectList(new LambdaQueryWrapper<LlmModelConfigDO>()
                .eq(LlmModelConfigDO::getIsActive, 1)
                .orderByDesc(LlmModelConfigDO::getCreateTime));
    }

    @Override
    public void toggleActive(Long id) {
        LlmModelConfigDO config = configMapper.selectById(id);
        if (config == null) {
            throw new IllegalArgumentException("模型配置不存在: " + id);
        }
        config.setIsActive(config.getIsActive() == 1 ? 0 : 1);
        config.setUpdateTime(new Date());
        configMapper.updateById(config);
    }

    @Override
    public boolean testConnectivity(Long id) {
        LlmModelConfigDO config = configMapper.selectById(id);
        if (config == null) {
            throw new IllegalArgumentException("模型配置不存在: " + id);
        }

        try {
            // 自动规范化 baseUrl，兼容用户填写的 /v1 后缀
            String normalizedUrl = normalizeBaseUrl(config.getBaseUrl());
            log.info("[模型连通性测试] 模型 {} 使用 baseUrl: {} (原始: {})", 
                    config.getModelName(), normalizedUrl, config.getBaseUrl());

            OpenAiApi openAiApi = OpenAiApi.builder()
                    .baseUrl(normalizedUrl)
                    .apiKey(getDecryptedApiKey(id))
                    .build();

            OpenAiChatModel chatModel = OpenAiChatModel.builder()
                    .openAiApi(openAiApi)
                    .defaultOptions(OpenAiChatOptions.builder()
                            .model(config.getModelId())
                            .maxTokens(config.getMaxTokens())
                            .temperature(config.getTemperature())
                            .build())
                    .build();

            ChatClient chatClient = ChatClient.builder(chatModel).build();
            String response = chatClient.prompt()
                    .user("Hello, this is a connectivity test. Reply with 'OK'.")
                    .call()
                    .content();

            log.info("[模型连通性测试] 模型 {} 测试成功，响应: {}", config.getModelName(), response);
            return response != null && !response.isBlank();
        } catch (Exception e) {
            log.error("[模型连通性测试] 模型 {} 测试失败: {}", config.getModelName(), e.getMessage(), e);
            return false;
        }
    }

    /**
     * 规范化 baseUrl，自动去掉末尾的 /v1
     * 
     * <p>Spring AI 的 OpenAiApi 内部会自动拼接 /v1/chat/completions，
     * 如果用户填写的 baseUrl 已经包含 /v1，会导致路径重复（如 /v1/v1/chat/completions）。
     * 
     * @param baseUrl 原始 baseUrl
     * @return 规范化后的 baseUrl（去掉末尾的 /v1）
     */
    private static String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return baseUrl;
        }
        String trimmed = baseUrl.trim();
        if (trimmed.endsWith("/v1")) {
            String normalized = trimmed.substring(0, trimmed.length() - 3);
            log.info("[BaseUrl 规范化] {} → {}", trimmed, normalized);
            return normalized;
        }
        return trimmed;
    }

    @Override
    public void delete(Long id) {
        configMapper.deleteById(id);
    }

    @Override
    public String getDecryptedApiKey(Long id) {
        LlmModelConfigDO config = configMapper.selectById(id);
        if (config == null) {
            throw new IllegalArgumentException("模型配置不存在: " + id);
        }
        // TODO: 接入 Jasypt 解密
        // 当前阶段直接返回明文，后续接入加密存储
        return config.getApiKey();
    }
}
