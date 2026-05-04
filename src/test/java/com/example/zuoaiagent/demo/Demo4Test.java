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
class Demo4Test {

   /* @Resource
    private Demo04 loveApp;

    @Test
    void testChat() {
        String chatId = UUID.randomUUID().toString();
        // 第一轮
        String message = "你好，我是程序员鱼皮";
        System.out.println("第一轮:"+message);
        String answer = loveApp.doChat(message, chatId);
        Assertions.assertNotNull(answer);
        // 第二轮
        message = "我想让另一半（编程导航）更爱我";
        System.out.println("第二轮:"+message);
        answer = loveApp.doChat(message, chatId);
        Assertions.assertNotNull(answer);
        // 第三轮
        message = "我的另一半叫什么来着？刚跟你说过，帮我回忆一下";
        System.out.println("第三轮:"+message);
        answer = loveApp.doChat(message, chatId);

        Assertions.assertNotNull(answer);
    }

    @Test
    void doChatWithReport() {
        String chatId = UUID.randomUUID().toString();
        // 第一轮
        String message = "你好，我是程序员鱼皮，我想让另一半（编程导航）更爱我，但我不知道该怎么做";
        Demo04.LoveReport loveReport = loveApp.doChatWithReport(message, chatId);
        Assertions.assertNotNull(loveReport);
    }*/




    @Resource
    private ChatServiceImpl chatServiceImpl;

    @Test
    void doChatWithRag() {
        String chatId = UUID.randomUUID().toString();
        String message = "宪法的发展历史是什么";
        String name="张三";
        String major="经济学";


        String answer =  chatServiceImpl.chatWithRag(message,chatId,name,major);
        Assertions.assertNotNull(answer);
    }



}