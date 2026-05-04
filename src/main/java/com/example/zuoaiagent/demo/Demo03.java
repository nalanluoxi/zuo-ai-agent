/*
package com.example.zuoaiagent.demo;


import jakarta.annotation.Resource;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.prompt.Prompt;


//@Component
@Profile("!test")
public class Demo03 implements CommandLineRunner {

        @Resource
        private ChatModel dashscopeChatModel;

        @Override
        public void run(String... args) throws Exception {
            AssistantMessage output = dashscopeChatModel.call(new Prompt("你好，我是李火旺"))
                    .getResult()
                    .getOutput();
            System.out.println(output.getText());
        }


}*/
