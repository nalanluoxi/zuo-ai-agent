package com.example.zuoaiagent.agent;

import com.example.zuoaiagent.intent.model.IntentResult;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AgentOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(AgentOrchestrator.class);

    private final AgentToolService agentToolService;

    public String executeAction(String userMessage, IntentResult intent) {
        log.info("[AgentOrchestrator] 执行动作: message={}", userMessage);
        return "正在处理您的请求：" + userMessage + "。如需执行具体操作，请明确说明。";
    }
}