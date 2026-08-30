package com.example.zuoaiagent.chat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class RoutingChatService {

    private static final Logger log = LoggerFactory.getLogger(RoutingChatService.class);

    private static final long PROBE_TIMEOUT_MS = 10_000L;

    private final ChatModelFactory factory;
    private final ChatCircuitBreaker circuitBreaker;
    private final ChatMemory chatMemory;

    public RoutingChatService(ChatModelFactory factory,
                              ChatCircuitBreaker circuitBreaker,
                              ChatMemory chatMemory) {
        this.factory = factory;
        this.circuitBreaker = circuitBreaker;
        this.chatMemory = chatMemory;
    }

    public String chat(String prompt, String conversationId, String systemPrompt, VectorStore vectorStore) {
        for (ChatModelFactory.ChatModelEntry entry : factory.getCandidates()) {
            if (!circuitBreaker.allowCall(entry.id())) {
                log.debug("[RoutingChatService] 候选 {} 被熔断，跳过", entry.id());
                continue;
            }

            try {
                String result = buildSpec(entry, prompt, conversationId, systemPrompt, vectorStore)
                        .call()
                        .content();
                circuitBreaker.markSuccess(entry.id());
                return result;
            } catch (Exception e) {
                log.warn("[RoutingChatService] 候选 {} 同步调用失败: {}", entry.id(), e.getMessage());
                circuitBreaker.markFailure(entry.id());
            }
        }
        throw new RuntimeException("所有 Chat 候选均失败，无法获取回复");
    }

    public void streamChat(String prompt, String conversationId,
                           String systemPrompt, VectorStore vectorStore,
                           SseEmitter emitter) {
        for (ChatModelFactory.ChatModelEntry entry : factory.getCandidates()) {
            if (!circuitBreaker.allowCall(entry.id())) {
                continue;
            }
            if (tryStreamWithEntry(entry, prompt, conversationId, systemPrompt, vectorStore, emitter)) {
                return;
            }
        }
        try {
            emitter.send(SseEmitter.event().name("error").data("所有 Chat 候选均失败，请稍后重试"));
        } catch (IOException ignored) {
        }
        emitter.complete();
    }

    private boolean tryStreamWithEntry(ChatModelFactory.ChatModelEntry entry,
                                       String prompt, String conversationId,
                                       String systemPrompt, VectorStore vectorStore,
                                       SseEmitter emitter) {
        CompletableFuture<Boolean> probeFuture = new CompletableFuture<>();
        AtomicBoolean probeCompleted = new AtomicBoolean(false);

        Flux<String> flux = buildSpec(entry, prompt, conversationId, systemPrompt, vectorStore)
                .stream()
                .content();

        final Disposable[] subscriptionHolder = new Disposable[1];

        subscriptionHolder[0] = flux.subscribe(
                chunk -> {
                    if (probeCompleted.compareAndSet(false, true)) {
                        circuitBreaker.markSuccess(entry.id());
                        probeFuture.complete(true);
                    }
                    try {
                        emitter.send(SseEmitter.event().data(chunk));
                    } catch (IOException e) {
                        log.warn("[RoutingChatService] SSE 发送失败", e);
                    }
                },
                error -> {
                    log.warn("[RoutingChatService] 候选 {} 流式调用失败: {}", entry.id(), error.getMessage());
                    circuitBreaker.markFailure(entry.id());
                    if (!probeCompleted.get()) {
                        probeFuture.complete(false);
                    } else {
                        try {
                            emitter.send(SseEmitter.event().name("error").data(error.getMessage()));
                        } catch (IOException ignored) {
                        }
                        emitter.completeWithError(error);
                    }
                },
                () -> {
                    if (probeCompleted.get()) {
                        try {
                            emitter.send(SseEmitter.event().data("[DONE]"));
                        } catch (IOException ignored) {
                        }
                        emitter.complete();
                    }
                }
        );

        try {
            return probeFuture.get(PROBE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            log.warn("[RoutingChatService] 候选 {} 首包探测超时", entry.id());
            circuitBreaker.markFailure(entry.id());
            subscriptionHolder[0].dispose();
            return false;
        } catch (InterruptedException | ExecutionException e) {
            log.warn("[RoutingChatService] 候选 {} 探测异常: {}", entry.id(), e.getMessage());
            circuitBreaker.markFailure(entry.id());
            return false;
        }
    }

    private ChatClient.ChatClientRequestSpec buildSpec(ChatModelFactory.ChatModelEntry entry,
                                                        String prompt, String conversationId,
                                                        String systemPrompt, VectorStore vectorStore) {
        ChatClient.ChatClientRequestSpec spec = ChatClient.builder(entry.delegate()).build().prompt()
                .user(prompt)
                .options(entry.buildOptions())
                .advisors(MessageChatMemoryAdvisor.builder(chatMemory).conversationId(conversationId).build());

        if (systemPrompt != null && !systemPrompt.isBlank()) {
            spec = spec.system(systemPrompt);
        }
        if (vectorStore != null) {
            spec = spec.advisors(new QuestionAnswerAdvisor(vectorStore));
        }
        return spec;
    }
}