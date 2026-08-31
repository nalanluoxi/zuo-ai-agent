package com.example.zuoaiagent.config;

import com.example.zuoaiagent.chatmemory.RedisChatMemory;
import com.example.zuoaiagent.prompt.PromptTemplateLoader;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class ChatClientConfig {

    @Value("${chat.memory.summary-start-turns:20}")
    private int summaryStartTurns;

    @Value("${chat.memory.compression-batch-size:20}")
    private int compressionBatchSize;

    @Value("${chat.memory.history-keep:20}")
    private int historyKeep;

    @Value("${chat.memory.session-ttl-minutes:1440}")
    private int sessionTtlMinutes;

    @Bean
    public ChatMemory chatMemory(StringRedisTemplate redisTemplate,
                                  RabbitTemplate rabbitTemplate,
                                  JdbcTemplate jdbcTemplate) {
        return new RedisChatMemory(redisTemplate, rabbitTemplate, jdbcTemplate,
                summaryStartTurns, compressionBatchSize, historyKeep, sessionTtlMinutes);
    }

    @Bean
    public ChatClient chatClient(@Qualifier("dashscopeChatModel") ChatModel chatModel,
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