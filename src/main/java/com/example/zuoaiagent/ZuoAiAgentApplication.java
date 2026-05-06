package com.example.zuoaiagent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springdoc.core.configuration.SpringDocKotlinConfiguration;

@SpringBootApplication(exclude = {SpringDocKotlinConfiguration.class})
public class ZuoAiAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(ZuoAiAgentApplication.class, args);
    }

}
