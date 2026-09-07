package com.example.zuoaiagent.config;

import com.example.zuoaiagent.chatmemory.RedisChatMemory;
import com.example.zuoaiagent.prompt.PromptTemplateLoader;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class ChatClientConfig {

    @Value("${spring.ai.ollama.base-url:http://localhost:11434}")
    private String ollamaBaseUrl;

    @Value("${chat.memory.summary-start-turns:20}")
    private int summaryStartTurns;

    @Value("${chat.memory.compression-batch-size:20}")
    private int compressionBatchSize;

    @Value("${chat.memory.history-keep:20}")
    private int historyKeep;

    @Value("${chat.memory.session-ttl-minutes:1440}")
    private int sessionTtlMinutes;

    /**
     * Ollama API 客户端 Bean
     * <p>因为排除了 OllamaAutoConfiguration，需要手动创建此 Bean
     */
    @Bean
    public OllamaApi ollamaApi() {
        return OllamaApi.builder()
                .baseUrl(ollamaBaseUrl)
                .build();
    }

    /**
     * 本地 Ollama Chat 模型 Bean
     * <p>使用 YAML 中配置的 qwen2.5:7b 模型，始终可用，不参与灰度发布
     */
    @Bean
    @Qualifier("ollamaChatModel")
    public OllamaChatModel ollamaChatModel(OllamaApi ollamaApi) {
        return OllamaChatModel.builder()
                .ollamaApi(ollamaApi)
                .defaultOptions(OllamaOptions.builder()
                        .model("qwen2.5:7b")
                        .build())
                .build();
    }

    /**
     * 本地 Ollama Embedding 模型 Bean
     * <p>使用写死的 bge-m3 模型，1024 维，始终可用
     */
    @Bean
    @Qualifier("ollamaEmbeddingModel")
    public OllamaEmbeddingModel ollamaEmbeddingModel(OllamaApi ollamaApi) {
        return OllamaEmbeddingModel.builder()
                .ollamaApi(ollamaApi)
                .defaultOptions(OllamaOptions.builder()
                        .model("bge-m3")
                        .build())
                .build();
    }

    @Bean
    public ChatMemory chatMemory(StringRedisTemplate redisTemplate,
                                  RabbitTemplate rabbitTemplate,
                                  JdbcTemplate jdbcTemplate) {
        return new RedisChatMemory(redisTemplate, rabbitTemplate, jdbcTemplate,
                summaryStartTurns, compressionBatchSize, historyKeep, sessionTtlMinutes);
    }

    @Bean
    public ChatClient chatClient(ChatModel chatModel,
                                  ChatMemory chatMemory,
                                  PromptTemplateLoader templateLoader) {
        String systemPrompt = templateLoader.load("prompts/system-chat.st");
        return ChatClient.builder(chatModel)
                .defaultSystem(systemPrompt)
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(chatMemory).build()
                )
                .build();
    }
}