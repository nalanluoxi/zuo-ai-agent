package com.example.zuoaiagent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan  // 扫描并注册所有 @ConfigurationProperties Bean（含 EmbeddingProperties）
public class ZuoAiAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(ZuoAiAgentApplication.class, args);
    }

}
