package com.example.zuoaiagent.pipeline;

import com.example.zuoaiagent.chat.RoutingChatService;
import com.example.zuoaiagent.intent.model.IntentResult;
import com.example.zuoaiagent.intent.service.IntentClassifier;
import com.example.zuoaiagent.memory.UserMemoryExtractionService;
import com.example.zuoaiagent.prompt.PromptScene;
import com.example.zuoaiagent.prompt.RAGPromptService;
import com.example.zuoaiagent.rag.*;
import com.example.zuoaiagent.trace.service.RagTraceRecordService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class SmartRagPipeline {

    private static final Logger log = LoggerFactory.getLogger(SmartRagPipeline.class);
    private static final double CONFIDENCE_THRESHOLD = 0.5;
    private static final double RERANK_CONFIDENCE_THRESHOLD = 0.5;
    private static final int RERANK_TOP_K = 3;
    private static final int TOKEN_BUDGET = 3000;

    private final QueryRewriter queryRewriter;
    private final HyDEQueryRewriter hydeQueryRewriter;
    private final IntentClassifier intentClassifier;
    private final MultiChannelRetriever multiChannelRetriever;
    private final DocumentReranker documentReranker;
    private final TokenBudgetTrimmer tokenBudgetTrimmer;
    private final RAGPromptService ragPromptService;
    private final RoutingChatService routingChatService;
    private final RagTraceRecordService traceService;
    private final UserMemoryExtractionService memoryExtractionService;
    private final ObjectMapper objectMapper;

    public SmartRagPipeline(QueryRewriter queryRewriter,
                            HyDEQueryRewriter hydeQueryRewriter,
                            IntentClassifier intentClassifier,
                            MultiChannelRetriever multiChannelRetriever,
                            DocumentReranker documentReranker,
                            TokenBudgetTrimmer tokenBudgetTrimmer,
                            RAGPromptService ragPromptService,
                            RoutingChatService routingChatService,
                            RagTraceRecordService traceService,
                            UserMemoryExtractionService memoryExtractionService,
                            ObjectMapper objectMapper) {
        this.queryRewriter = queryRewriter;
        this.hydeQueryRewriter = hydeQueryRewriter;
        this.intentClassifier = intentClassifier;
        this.multiChannelRetriever = multiChannelRetriever;
        this.documentReranker = documentReranker;
        this.tokenBudgetTrimmer = tokenBudgetTrimmer;
        this.ragPromptService = ragPromptService;
        this.routingChatService = routingChatService;
        this.traceService = traceService;
        this.memoryExtractionService = memoryExtractionService;
        this.objectMapper = objectMapper;
    }

    public void execute(RagPipelineContext ctx, SseEmitter emitter) {
        String traceId = UUID.randomUUID().toString().replace("-", "");
        String conId = ctx.getConversationId();
        long pipelineStart = System.currentTimeMillis();

        traceService.startRun(traceId, conId, ctx.getOriginalPrompt());
        ctx.setTraceId(traceId);

        try {
            String rewrittenQuery = executeRewrite(ctx, traceId);

            IntentResult intentResult = executeClassify(rewrittenQuery, traceId);
            ctx.setIntentResult(intentResult);

            String domain = resolveDomain(intentResult);

            if (intentResult.isSystem()) {
                String memory = ctx.isEnableMemory() ? memoryExtractionService.getMemoryContext(ctx.getUserId()) : null;
                String sysPrompt = ragPromptService.build(PromptScene.SYSTEM_CHAT, ctx.getName(), domain, List.of());
                if (memory != null) sysPrompt = memory + "\n" + sysPrompt;
                ctx.setFinalSystemPrompt(sysPrompt);
                traceService.finishRun(traceId, "SUCCESS", null, System.currentTimeMillis() - pipelineStart);
                routingChatService.streamChat(ctx.getOriginalPrompt(), conId, sysPrompt, null, emitter);
                return;
            }

            List<Document> retrieved = executeRetrieve(rewrittenQuery, intentResult, traceId);
            ctx.setRetrievedDocs(retrieved);

            List<Document> reranked = executeRerank(ctx, rewrittenQuery, retrieved, traceId);
            ctx.setRerankedDocs(reranked);

            List<Document> trimmed = tokenBudgetTrimmer.trim(reranked, TOKEN_BUDGET);

            String memory = ctx.isEnableMemory() ? memoryExtractionService.getMemoryContext(ctx.getUserId()) : null;
            String finalSystemPrompt = executePromptBuild(ctx, domain, trimmed, memory, traceId);
            ctx.setFinalSystemPrompt(finalSystemPrompt);

            traceService.finishRun(traceId, "SUCCESS", null, System.currentTimeMillis() - pipelineStart);
            routingChatService.streamChat(ctx.getOriginalPrompt(), conId, finalSystemPrompt, null, emitter);

        } catch (Exception e) {
            log.error("[SmartRagPipeline] traceId={} 流水线异常: {}", traceId, e.getMessage(), e);
            traceService.finishRun(traceId, "ERROR", e.getMessage(), System.currentTimeMillis() - pipelineStart);
            throw e;
        }
    }

    private String executeRewrite(RagPipelineContext ctx, String traceId) {
        long start = System.currentTimeMillis();
        traceService.startNode(traceId, "rewrite", "查询改写", "REWRITE",
                toJson(Map.of("originalPrompt", ctx.getOriginalPrompt(), "enableRewrite", ctx.isEnableRewrite())));

        try {
            String rewrittenQuery = ctx.isEnableRewrite()
                    ? queryRewriter.rewrite(ctx.getOriginalPrompt())
                    : ctx.getOriginalPrompt();

            boolean hydeUsed = false;
            if (hydeQueryRewriter.shouldUseHyDE()) {
                String hydeDoc = hydeQueryRewriter.generateHypothesisDocument(rewrittenQuery);
                if (!hydeDoc.isBlank()) {
                    rewrittenQuery = rewrittenQuery + " " + hydeDoc;
                    hydeUsed = true;
                }
            }

            ctx.setRewrittenQuery(rewrittenQuery);
            int promptTokens = estimateTokens(ctx.getOriginalPrompt());
            int completionTokens = estimateTokens(rewrittenQuery);
            traceService.finishNode(traceId, "rewrite", "SUCCESS", null,
                    System.currentTimeMillis() - start,
                    toJson(Map.of("rewrittenQuery", rewrittenQuery, "hydeUsed", hydeUsed)),
                    promptTokens, completionTokens);
            return rewrittenQuery;
        } catch (Exception e) {
            traceService.finishNode(traceId, "rewrite", "ERROR", e.getMessage(),
                    System.currentTimeMillis() - start, null);
            return ctx.getOriginalPrompt();
        }
    }

    private IntentResult executeClassify(String query, String traceId) {
        long start = System.currentTimeMillis();
        traceService.startNode(traceId, "classify", "意图分类", "CLASSIFY", toJson(Map.of("query", query)));

        try {
            IntentResult result = intentClassifier.classify(query);
            Map<String, Object> output = new HashMap<>();
            output.put("intentNodeId", result.getIntentNodeId());
            output.put("label", result.getLabel());
            output.put("confidence", result.getConfidence());
            output.put("isSystem", result.isSystem());
            output.put("kbId", result.getKbId());
            traceService.finishNode(traceId, "classify", "SUCCESS", null,
                    System.currentTimeMillis() - start, toJson(output),
                    estimateTokens(query), estimateTokens(String.valueOf(result.getIntentNodeId())));
            return result;
        } catch (Exception e) {
            traceService.finishNode(traceId, "classify", "ERROR", e.getMessage(),
                    System.currentTimeMillis() - start, null);
            return IntentResult.unknown();
        }
    }

    private List<Document> executeRetrieve(String query, IntentResult intentResult, String traceId) {
        long start = System.currentTimeMillis();
        Long kbId = (intentResult.getConfidence() >= CONFIDENCE_THRESHOLD) ? intentResult.getKbId() : null;

        traceService.startNode(traceId, "retrieve", "多通道检索", "RETRIEVE",
                toJson(Map.of("query", query, "kbId", kbId)));

        try {
            List<Document> docs = multiChannelRetriever.retrieve(query, kbId);
            traceService.finishNode(traceId, "retrieve", "SUCCESS", null,
                    System.currentTimeMillis() - start, toJson(Map.of("count", docs.size())));
            return docs;
        } catch (Exception e) {
            traceService.finishNode(traceId, "retrieve", "ERROR", e.getMessage(),
                    System.currentTimeMillis() - start, null);
            return List.of();
        }
    }

    private List<Document> executeRerank(RagPipelineContext ctx, String query,
                                         List<Document> retrieved, String traceId) {
        long start = System.currentTimeMillis();
        traceService.startNode(traceId, "rerank", "重排序", "RERANK",
                toJson(Map.of("inputCount", retrieved.size(), "enableRerank", ctx.isEnableRerank())));

        try {
            List<Document> finalDocs = ctx.isEnableRerank()
                    ? documentReranker.rerank(query, retrieved, RERANK_TOP_K, RERANK_CONFIDENCE_THRESHOLD)
                    : (retrieved.size() > RERANK_TOP_K ? retrieved.subList(0, RERANK_TOP_K) : retrieved);

            traceService.finishNode(traceId, "rerank", "SUCCESS", null,
                    System.currentTimeMillis() - start, toJson(Map.of("count", finalDocs.size())),
                    estimateTokens(query) * retrieved.size(), estimateTokens("score") * retrieved.size());
            return finalDocs;
        } catch (Exception e) {
            traceService.finishNode(traceId, "rerank", "ERROR", e.getMessage(),
                    System.currentTimeMillis() - start, null);
            return retrieved.size() > RERANK_TOP_K ? retrieved.subList(0, RERANK_TOP_K) : retrieved;
        }
    }

    private String executePromptBuild(RagPipelineContext ctx, String domain,
                                      List<Document> finalDocs, String memory, String traceId) {
        long start = System.currentTimeMillis();
        traceService.startNode(traceId, "prompt", "Prompt组装", "PROMPT",
                toJson(Map.of("docCount", finalDocs.size(), "domain", domain)));

        PromptScene scene = finalDocs.isEmpty() ? PromptScene.EMPTY_RETRIEVAL : PromptScene.KB_ONLY;
        String prompt = ragPromptService.build(scene, ctx.getName(), domain, finalDocs);
        if (memory != null && !memory.isBlank()) {
            prompt = memory + "\n" + prompt;
        }

        traceService.finishNode(traceId, "prompt", "SUCCESS", null,
                System.currentTimeMillis() - start, toJson(Map.of("scene", scene.name(), "promptLength", prompt.length())));
        return prompt;
    }

    private String resolveDomain(IntentResult intentResult) {
        if (intentResult == null) return "各领域";
        if (intentResult.isSystem() || intentResult.getConfidence() == 0.0) return "各领域";
        return intentResult.getLabel();
    }

    private String toJson(Object obj) {
        try { return objectMapper.writeValueAsString(obj); }
        catch (JsonProcessingException e) { return "{}"; }
    }

    private int estimateTokens(String text) {
        if (text == null || text.isBlank()) return 0;
        return (int) Math.ceil(text.length() * 0.4);
    }
}