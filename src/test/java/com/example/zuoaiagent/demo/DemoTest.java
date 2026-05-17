package com.example.zuoaiagent.demo;

import com.example.zuoaiagent.service.impl.ChatServiceImpl;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;



@SpringBootTest
@ActiveProfiles("test")
public class DemoTest {

    @Resource
    private ChatServiceImpl chatServiceImpl;

    @Test
    void doChatWithRag() {
        String chatId = UUID.randomUUID().toString();
        String message = "宪法的发展历史是什么";
        String name="张三";
        String major="经济学";


        String answer =  chatServiceImpl.chatWithRag(message,chatId,name);
        Assertions.assertNotNull(answer);
    }

}
