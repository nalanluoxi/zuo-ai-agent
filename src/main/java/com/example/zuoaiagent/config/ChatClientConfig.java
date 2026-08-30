package com.example.zuoaiagent.config;

import com.example.zuoaiagent.chatmemory.RedisChatMemory;
import com.example.zuoaiagent.constants.SystemConstants;
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

@Configuration
public class ChatClientConfig {

    @Value("${chat.memory.summary-start-turns:10}")
    private int summaryStartTurns;

    @Value("${chat.memory.history-keep:20}")
    private int historyKeep;

    @Value("${chat.memory.session-ttl-minutes:1440}")
    private int sessionTtlMinutes;

    @Bean
    public ChatMemory chatMemory(StringRedisTemplate redisTemplate,
                                  RabbitTemplate rabbitTemplate) {
        return new RedisChatMemory(redisTemplate, rabbitTemplate,
                summaryStartTurns, historyKeep, sessionTtlMinutes);
    }

    @Bean
    public ChatClient chatClient(@Qualifier("dashscopeChatModel") ChatModel chatModel,
                                  ChatMemory chatMemory) {
        return ChatClient.builder(chatModel)
                .defaultSystem(SystemConstants.SYSTEM_PROMPT)
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(chatMemory).build()
                )
                .build();
    }
}