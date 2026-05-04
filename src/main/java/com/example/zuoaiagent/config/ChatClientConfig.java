package com.example.zuoaiagent.config;

import com.example.zuoaiagent.advisor.MyLoggerAdvisor;
import com.example.zuoaiagent.chatmemory.FileBasedChatMemory;
import com.example.zuoaiagent.chatmemory.MapBasedChatMemory;
import com.example.zuoaiagent.constants.SystemConstants;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


@Configuration
public class ChatClientConfig {

/*
 @Bean
    public ChatMemory chatMemory() {
        String fileDir = System.getProperty("user.dir") + "/chat-memory";
        return new FileBasedChatMemory(fileDir);
    }*/


    @Bean
    public ChatMemory chatMemory() {
        return new MapBasedChatMemory();
    }

    @Bean
    public ChatClient chatClient(ChatModel dashscopeChatModel, ChatMemory chatMemory) {
        return ChatClient.builder(dashscopeChatModel)
                .defaultSystem(SystemConstants.SYSTEM_PROMPT)
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(chatMemory).build()
                )
                .build();
    }
}
