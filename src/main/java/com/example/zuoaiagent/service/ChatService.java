package com.example.zuoaiagent.service;

import com.example.zuoaiagent.model.Student;

public interface ChatService {
    String chat(String prompt, String conId);

    Student chat2(String prompt, String conId);

    String chatWithRag(String prompt, String conId,String name,String major);
}
