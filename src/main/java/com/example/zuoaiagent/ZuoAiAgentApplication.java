package com.example.zuoaiagent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan  // 扫描并注册所有 @ConfigurationProperties Bean（含 EmbeddingProperties）
@EnableScheduling  // 启用定时任务（心跳上报等）
public class ZuoAiAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(ZuoAiAgentApplication.class, args);
    }

}
