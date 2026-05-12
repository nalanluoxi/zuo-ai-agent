package com.example.zuoaiagent.service.impl;

import com.example.zuoaiagent.advisor.MyLoggerAdvisor;
import com.example.zuoaiagent.constants.SystemConstants;
import com.example.zuoaiagent.model.Student;
import com.example.zuoaiagent.service.ChatService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;

@Service
public class ChatServiceImpl implements ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatServiceImpl.class);

    @Autowired
    private ChatClient chatClient;

    @Autowired
    private ChatMemory chatMemory;

    @Autowired
    private VectorStore vectorStore;


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

    @Override
    public String chatWithRag(String prompt, String conId,String name,String major) {
        PromptTemplate promptTemplate = new PromptTemplate(SystemConstants.SYSTEM_MASTER_PROMPT);
        HashMap<String, Object> map=new HashMap<>();
        map.put("name",name);
        map.put("major",major);
        String systemPrompt = promptTemplate.render(map);
        log.info("系统提示词:"+systemPrompt);

        ChatResponse chatResponse = chatClient
                .prompt()
                .system(systemPrompt)
                .user(prompt)
                .advisors(
                        MessageChatMemoryAdvisor.builder(chatMemory).conversationId(conId).build(),
                        new MyLoggerAdvisor(),
                        new QuestionAnswerAdvisor(vectorStore)
                )
                .call()
                .chatResponse();
        String content = chatResponse.getResult().getOutput().getText();
        log.info("content: {}", content);
        return content;
    }





}
