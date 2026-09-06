package com.example.zuoaiagent.advisor;

import lombok.extern.slf4j.Slf4j;

import org.springframework.ai.chat.client.ChatClientMessageAggregator;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import reactor.core.publisher.Flux;

@Slf4j
public class MyLoggerAdvisor implements CallAdvisor, StreamAdvisor {

    //针对于非流式处理
    @Override
    public ChatClientResponse adviseCall(ChatClientRequest chatClientRequest, CallAdvisorChain chain) {
        chatClientRequest = before(chatClientRequest);
        ChatClientResponse chatClientResponse = chain.nextCall(chatClientRequest);
        after(chatClientResponse);
        return chatClientResponse;
    }

    //针对于流式处理
    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest chatClientRequest, StreamAdvisorChain chain) {
        chatClientRequest=before(chatClientRequest);
        Flux<ChatClientResponse> chatClientResponseFlux = chain.nextStream(chatClientRequest);
        return (new ChatClientMessageAggregator()).aggregateChatClientResponse(chatClientResponseFlux, this::after);
    }

    @Override
    public String getName() {
        return "MyLoggerAdvisor 日志处理助理";
    }

    @Override
    public int getOrder() {
        return Integer.MAX_VALUE - 2;
        //return 0;
    }


    private ChatClientRequest before(ChatClientRequest request) {
        System.out.println("=== MyLoggerAdvisor before ===");
        log.info("AI 的请求Request: {}", request.prompt());
        return request;
    }

    private void after(ChatClientResponse chatClientResponse) {
        log.info("AI 的响应有Response: {}", chatClientResponse.chatResponse().getResult().getOutput().getText());
    }
}
