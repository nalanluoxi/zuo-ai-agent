package com.example.zuoaiagent.chat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

import com.example.zuoaiagent.dashboard.service.TokenUsageService;
import com.example.zuoaiagent.prompt.PromptTemplateLoader;

import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class RoutingChatService {

    private static final Logger log = LoggerFactory.getLogger(RoutingChatService.class);

    private static final long PROBE_TIMEOUT_MS = 10_000L;
    private static final String DEFAULT_SYSTEM_PROMPT_PATH = "prompts/system-chat.st";

    private final ChatModelFactory factory;
    private final ChatCircuitBreaker circuitBreaker;
    private final ChatMemory chatMemory;
    private final PromptTemplateLoader templateLoader;
    private final TokenUsageService tokenUsageService;
    private volatile String cachedSystemPrompt;

    public RoutingChatService(ChatModelFactory factory,
                              ChatCircuitBreaker circuitBreaker,
                              ChatMemory chatMemory,
                              PromptTemplateLoader templateLoader,
                              TokenUsageService tokenUsageService) {
        this.factory = factory;
        this.circuitBreaker = circuitBreaker;
        this.chatMemory = chatMemory;
        this.templateLoader = templateLoader;
        this.tokenUsageService = tokenUsageService;
    }

    private String getDefaultSystemPrompt() {
        if (cachedSystemPrompt == null) {
            synchronized (this) {
                if (cachedSystemPrompt == null) {
                    cachedSystemPrompt = templateLoader.load(DEFAULT_SYSTEM_PROMPT_PATH);
                }
            }
        }
        return cachedSystemPrompt;
    }

    public String chat(String prompt, String conversationId, String systemPrompt, VectorStore vectorStore) {
        return chat(prompt, conversationId, systemPrompt, vectorStore, false);
    }

    public String chat(String prompt, String conversationId, String systemPrompt, VectorStore vectorStore, boolean internal) {
        for (ChatModelFactory.ChatModelEntry entry : factory.getCandidates()) {
            if (!circuitBreaker.allowCall(entry.id())) {
                log.debug("[RoutingChatService] 候选 {} 被熔断，跳过", entry.id());
                continue;
            }

            try {
                String result = buildSpec(entry, prompt, conversationId, systemPrompt, vectorStore, internal)
                        .call()
                        .content();
                circuitBreaker.markSuccess(entry.id());
                if (!internal && result != null) {
                    chatMemory.add(conversationId, new AssistantMessage(result));
                }
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
        streamChat(prompt, conversationId, systemPrompt, vectorStore, emitter, false, null);
    }

    public void streamChat(String prompt, String conversationId,
                           String systemPrompt, VectorStore vectorStore,
                           SseEmitter emitter, boolean internal) {
        streamChat(prompt, conversationId, systemPrompt, vectorStore, emitter, internal, null);
    }

    public void streamChat(String prompt, String conversationId,
                           String systemPrompt, VectorStore vectorStore,
                           SseEmitter emitter, boolean internal, Long userId) {
        for (ChatModelFactory.ChatModelEntry entry : factory.getCandidates()) {
            if (!circuitBreaker.allowCall(entry.id())) {
                continue;
            }
            if (tryStreamWithEntry(entry, prompt, conversationId, systemPrompt, vectorStore, emitter, internal, userId)) {
                return;
            }
        }
        if (!internal) {
            try {
                emitter.send(SseEmitter.event().name("error").data("所有 Chat 候选均失败，请稍后重试"));
            } catch (IOException ignored) {
            }
            emitter.complete();
        }
    }

    private boolean tryStreamWithEntry(ChatModelFactory.ChatModelEntry entry,
                                       String prompt, String conversationId,
                                       String systemPrompt, VectorStore vectorStore,
                                       SseEmitter emitter, boolean internal, Long userId) {
        CompletableFuture<Boolean> probeFuture = new CompletableFuture<>();
        AtomicBoolean probeCompleted = new AtomicBoolean(false);
        AtomicBoolean sendFailed = new AtomicBoolean(false);
        StringBuilder responseAccumulator = new StringBuilder();
        long startTime = System.currentTimeMillis();
        int[] tokenUsage = new int[]{0, 0};

        Flux<ChatResponse> responseFlux = buildSpec(entry, prompt, conversationId, systemPrompt, vectorStore, internal)
                .stream()
                .chatResponse();

        Flux<String> flux = responseFlux.map(cr -> {
            // 累积真实 Token 使用量（Ollama 在最后一个 chunk 中返回完整 usage）
            Usage usage = cr.getMetadata() != null ? cr.getMetadata().getUsage() : null;
            if (usage != null) {
                tokenUsage[0] = usage.getPromptTokens() != null ? usage.getPromptTokens() : 0;
                tokenUsage[1] = usage.getCompletionTokens() != null ? usage.getCompletionTokens() : 0;
            }
            return cr.getResult() != null && cr.getResult().getOutput() != null
                    ? cr.getResult().getOutput().getText() : "";
        }).filter(s -> !s.isEmpty());

        final Disposable[] subscriptionHolder = new Disposable[1];

        // 设置超时和完成回调，确保资源清理
        emitter.onTimeout(() -> {
            log.warn("[RoutingChatService] SSE 连接超时，conversationId={}", conversationId);
            if (subscriptionHolder[0] != null && !subscriptionHolder[0].isDisposed()) {
                subscriptionHolder[0].dispose();
            }
        });

        emitter.onCompletion(() -> {
            if (subscriptionHolder[0] != null && !subscriptionHolder[0].isDisposed()) {
                subscriptionHolder[0].dispose();
            }
        });

        subscriptionHolder[0] = flux.subscribe(
                chunk -> {
                    responseAccumulator.append(chunk);
                    if (probeCompleted.compareAndSet(false, true)) {
                        circuitBreaker.markSuccess(entry.id());
                        probeFuture.complete(true);
                    }
                    // 如果之前发送失败，不再继续发送
                    if (sendFailed.get()) {
                        return;
                    }
                    try {
                        emitter.send(SseEmitter.event().data(chunk));
                    } catch (Exception e) {
                        log.warn("[RoutingChatService] SSE 发送失败，标记为损坏: {}", e.getClass().getSimpleName());
                        sendFailed.set(true);
                    }
                },
                error -> {
                    log.warn("[RoutingChatService] 候选 {} 流式调用失败: {}", entry.id(), error.getMessage());
                    circuitBreaker.markFailure(entry.id());
                    if (!probeCompleted.get()) {
                        probeFuture.complete(false);
                    } else if (!sendFailed.get()) {
                        try {
                            emitter.send(SseEmitter.event().name("error").data(error.getMessage()));
                        } catch (Exception ignored) {
                            sendFailed.set(true);
                        }
                        emitter.completeWithError(error);
                    }
                },
                () -> {
                    String fullResponse = responseAccumulator.toString();
                    if (!internal && !fullResponse.isEmpty()) {
                        chatMemory.add(conversationId, new AssistantMessage(fullResponse));

                        int inputTokens = tokenUsage[0];
                        int outputTokens = tokenUsage[1];
                        // 如果模型未返回真实 token（如 Ollama 某些版本），回退到估算
                        if (inputTokens == 0 && outputTokens == 0) {
                            inputTokens = estimateTokens(prompt);
                            outputTokens = estimateTokens(fullResponse);
                            log.debug("[RoutingChatService] 模型未返回 token 统计，使用估算值: input={}, output={}", inputTokens, outputTokens);
                        }
                        int totalTokens = inputTokens + outputTokens;

                        try {
                            tokenUsageService.recordUsage(
                                userId != null ? String.valueOf(userId) : null,
                                conversationId,
                                UUID.randomUUID().toString(),
                                entry.modelName(),
                                inputTokens,
                                outputTokens,
                                totalTokens
                            );
                        } catch (Exception e) {
                            log.warn("记录 token 使用量失败", e);
                        }
                    }
                    if (probeCompleted.get() && !sendFailed.get()) {
                        try {
                            emitter.send(SseEmitter.event().data("[DONE]"));
                        } catch (Exception ignored) {
                            sendFailed.set(true);
                        }
                        if (!sendFailed.get()) {
                            emitter.complete();
                        }
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

    /**
     * 构建 ChatClientRequestSpec，手动管理消息顺序以解决 MessageChatMemoryAdvisor 的顺序问题。
     *
     * 消息顺序: SystemMessage → 历史消息(旧→新) → 当前 UserMessage
     *
     * 不使用 MessageChatMemoryAdvisor，因为它会将历史消息插入到 SystemMessage 之前，
     * 导致 LLM API 忽略 SystemMessage。
     *
     * @param internal 是否为内部调用（意图分类、查询改写等），内部调用不读写 memory
     */
    private ChatClient.ChatClientRequestSpec buildSpec(ChatModelFactory.ChatModelEntry entry,
                                                        String prompt, String conversationId,
                                                        String systemPrompt, VectorStore vectorStore,
                                                        boolean internal) {
        String effectiveSystemPrompt = (systemPrompt != null && !systemPrompt.isBlank())
                ? systemPrompt
                : getDefaultSystemPrompt();

        List<Message> allMessages = new ArrayList<>();
        allMessages.add(new SystemMessage(effectiveSystemPrompt));

        if (!internal) {
            List<Message> history = chatMemory.get(conversationId);
            log.info("[RoutingChatService] conversationId={}, 历史消息条数: {}", conversationId, history.size());
            allMessages.addAll(history);
            chatMemory.add(conversationId, new UserMessage(prompt));
        }

        allMessages.add(new UserMessage(prompt));

        ChatClient.ChatClientRequestSpec spec = ChatClient.builder(entry.delegate()).build().prompt()
                .messages(allMessages)
                .options(entry.buildOptions());

        if (vectorStore != null) {
            spec = spec.advisors(new QuestionAnswerAdvisor(vectorStore));
        }
        return spec;
    }

    /**
     * 估算 token 数量（粗略估算，1 token ≈ 4 字符）
     */
    private int estimateTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return (int) (text.length() / 4.0);
    }
}
