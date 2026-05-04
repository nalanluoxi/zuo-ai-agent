package com.example.zuoaiagent.service.impl;

import com.example.zuoaiagent.model.Student;
import com.example.zuoaiagent.service.ChatService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class ChatServiceImpl implements ChatService {

    @Autowired
    private ChatClient chatClient;

    @Autowired
    private ChatMemory chatMemory;


    @Override
    public String chat(String prompt, String conId) {
        ChatResponse response = chatClient
                .prompt()
                .user(prompt)
                .advisors(MessageChatMemoryAdvisor.builder(chatMemory).conversationId(conId).build())
                .call()
                .chatResponse();
        String content = response.getResult().getOutput().getText();
        log.info("content: {}", content);
        return content;
    }

    @Override
    public Student chat2(String prompt, String conId) {
       Student student = chatClient
                .prompt()
                .user(prompt)
                .advisors(MessageChatMemoryAdvisor.builder(chatMemory).conversationId(conId).build())
                .call()
                .entity(Student.class);
       return student;
    }
}
