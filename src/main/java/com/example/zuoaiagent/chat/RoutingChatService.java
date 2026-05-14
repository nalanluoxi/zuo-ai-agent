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

/**
 * Chat 路由服务
 *
 * <p>封装多候选模型路由与熔断，对上层暴露两个入口：
 * <ul>
 *   <li>{@link #chat} — 同步调用，返回完整响应字符串</li>
 *   <li>{@link #streamChat} — 异步 SSE 流式推送，带首包探测</li>
 * </ul>
 */
@Service
public class RoutingChatService {

    private static final Logger log = LoggerFactory.getLogger(RoutingChatService.class);

    /** 首包探测超时时间（毫秒） */
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

    // ─────────────────────────── 同步路由 ───────────────────────────

    /**
     * 同步 Chat：遍历候选列表，带熔断保护，成功则返回，全部失败抛异常。
     *
     * @param prompt         用户消息
     * @param conversationId 会话 ID（用于记忆）
     * @param systemPrompt   系统提示词，为空则跳过
     * @param vectorStore    向量库（RAG），为空则跳过
     * @return 模型回复文本
     */
    public String chat(String prompt, String conversationId, String systemPrompt, VectorStore vectorStore) {
        int total = factory.size();
        for (int i = 0; i < total; i++) {
            ChatModelFactory.ChatModelEntry entry = factory.poll();
            if (entry == null) break;

            if (!circuitBreaker.allowCall(entry.id())) {
                factory.offerTail(entry);
                continue;
            }

            try {
                String result = buildSpec(entry, prompt, conversationId, systemPrompt, vectorStore)
                        .call()
                        .content();
                circuitBreaker.markSuccess(entry.id());
                factory.offerTail(entry);
                return result;
            } catch (Exception e) {
                log.warn("[RoutingChatService] 候选 {} 同步调用失败: {}", entry.id(), e.getMessage());
                circuitBreaker.markFailure(entry.id());
                factory.offerTail(entry);
            }
        }
        throw new RuntimeException("所有 Chat 候选均失败，无法获取回复");
    }

    // ─────────────────────────── 流式路由 ───────────────────────────

    /**
     * 异步 SSE 流式 Chat：带首包探测，成功后持续推送；全部失败则关闭 emitter 并报错。
     *
     * @param prompt         用户消息
     * @param conversationId 会话 ID
     * @param systemPrompt   系统提示词，为空则跳过
     * @param vectorStore    向量库（RAG），为空则跳过
     * @param emitter        SSE 输出通道
     */
    public void streamChat(String prompt, String conversationId,
                           String systemPrompt, VectorStore vectorStore,
                           SseEmitter emitter) {
        int total = factory.size();
        for (int i = 0; i < total; i++) {
            ChatModelFactory.ChatModelEntry entry = factory.poll();
            if (entry == null) break;

            if (!circuitBreaker.allowCall(entry.id())) {
                factory.offerTail(entry);
                continue;
            }

            if (tryStreamWithEntry(entry, prompt, conversationId, systemPrompt, vectorStore, emitter)) {
                return; // 成功，emitter 已被接管
            }
            factory.offerTail(entry);
        }

        // 所有候选均失败
        try {
            emitter.send(SseEmitter.event().name("error").data("所有 Chat 候选均失败，请稍后重试"));
        } catch (IOException ignored) {
        }
        emitter.complete();
    }

    /**
     * 尝试用指定候选进行流式调用，带首包探测。
     *
     * @return {@code true} 表示探测成功，流已接管 emitter；{@code false} 表示失败，应切换下一候选
     */
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
                    // 首包探测：第一个 chunk 表示成功
                    if (probeCompleted.compareAndSet(false, true)) {
                        circuitBreaker.markSuccess(entry.id());
                        factory.offerTail(entry);
                        probeFuture.complete(true);
                    }
                    try {
                        emitter.send(SseEmitter.event().data(chunk));
                    } catch (IOException e) {
                        log.debug("[RoutingChatService] SSE 发送失败（客户端断连）: {}", e.getMessage());
                        subscriptionHolder[0].dispose();
                    }
                },
                error -> {
                    log.warn("[RoutingChatService] 候选 {} 流式调用失败: {}", entry.id(), error.getMessage());
                    circuitBreaker.markFailure(entry.id());
                    if (probeCompleted.compareAndSet(false, true)) {
                        probeFuture.complete(false);
                    } else {
                        // 探测已成功但后续出错，通知客户端并关闭
                        try {
                            emitter.send(SseEmitter.event().name("error").data("[流中断]"));
                        } catch (IOException ignored) {
                        }
                        emitter.completeWithError(error);
                    }
                },
                () -> {
                    if (probeCompleted.compareAndSet(false, true)) {
                        // 空流（无 chunk）视为失败
                        probeFuture.complete(false);
                    } else {
                        // 正常结束
                        emitter.complete();
                    }
                }
        );

        try {
            boolean success = probeFuture.get(PROBE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            if (!success) {
                subscriptionHolder[0].dispose();
            }
            return success;
        } catch (TimeoutException e) {
            log.warn("[RoutingChatService] 候选 {} 首包超时（{} ms）", entry.id(), PROBE_TIMEOUT_MS);
            circuitBreaker.markFailure(entry.id());
            subscriptionHolder[0].dispose();
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            subscriptionHolder[0].dispose();
            return false;
        } catch (ExecutionException e) {
            log.warn("[RoutingChatService] 候选 {} 探测异常: {}", entry.id(), e.getCause().getMessage());
            circuitBreaker.markFailure(entry.id());
            subscriptionHolder[0].dispose();
            return false;
        }
    }

    /** 构建带有候选模型 options、记忆 advisor、可选系统提示词与 RAG advisor 的请求 spec */
    private ChatClient.ChatClientRequestSpec buildSpec(ChatModelFactory.ChatModelEntry entry,
                                                       String prompt, String conversationId,
                                                       String systemPrompt, VectorStore vectorStore) {
        ChatOptions options = entry.buildOptions();

        var spec = ChatClient.builder(entry.delegate())
                .build()
                .prompt()
                .options(options)
                .user(prompt)
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
