package com.example.zuoaiagent.service;

import com.example.zuoaiagent.model.Student;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface ChatService {
    String chat(String prompt, String conId);

    Student chat2(String prompt, String conId);

    String chatWithRag(String prompt, String conId, String name, String major);

    void streamChat(String prompt, String conversationId, SseEmitter emitter);

    void streamChatWithRag(String prompt, String conversationId, String name, String major, SseEmitter emitter);
}
