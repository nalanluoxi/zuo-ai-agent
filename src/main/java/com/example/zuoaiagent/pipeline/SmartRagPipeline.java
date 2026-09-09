package com.example.zuoaiagent.pipeline;

import com.example.zuoaiagent.chat.ChatModelFactory;
import com.example.zuoaiagent.chat.RoutingChatService;
import com.example.zuoaiagent.dashboard.service.RetrievalLogService;
import com.example.zuoaiagent.intent.model.IntentResult;
import com.example.zuoaiagent.intent.service.IntentClassifier;
import com.example.zuoaiagent.log.LogEventCollector;
import com.example.zuoaiagent.memory.UserMemoryExtractionService;
import com.example.zuoaiagent.prompt.PromptScene;
import com.example.zuoaiagent.prompt.PromptTemplateLoader;
import com.example.zuoaiagent.prompt.RAGPromptService;
import com.example.zuoaiagent.rag.*;
import com.example.zuoaiagent.raglab.entity.RagConfigDO;
import com.example.zuoaiagent.raglab.interceptor.GrayContextHolder;
import com.example.zuoaiagent.raglab.service.RagConfigLoader;
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

    private final QueryRewriter queryRewriter;
    private final HyDEQueryRewriter hydeQueryRewriter;
    private final IntentClassifier intentClassifier;
    private final MultiChannelRetriever multiChannelRetriever;
    private final DocumentReranker documentReranker;
    private final TokenBudgetTrimmer tokenBudgetTrimmer;
    private final RAGPromptService ragPromptService;
    private final PromptTemplateLoader templateLoader;
    private final RoutingChatService routingChatService;
    private final ChatModelFactory chatModelFactory;
    private final RagTraceRecordService traceService;
    private final UserMemoryExtractionService memoryExtractionService;
    private final RagConfigLoader configLoader;
    private final ObjectMapper objectMapper;
    private final RetrievalLogService retrievalLogService;
    private final LogEventCollector logEventCollector;

    public SmartRagPipeline(QueryRewriter queryRewriter,
                            HyDEQueryRewriter hydeQueryRewriter,
                            IntentClassifier intentClassifier,
                            MultiChannelRetriever multiChannelRetriever,
                            DocumentReranker documentReranker,
                            TokenBudgetTrimmer tokenBudgetTrimmer,
                            RAGPromptService ragPromptService,
                            PromptTemplateLoader templateLoader,
                            RoutingChatService routingChatService,
                            ChatModelFactory chatModelFactory,
                            RagTraceRecordService traceService,
                            UserMemoryExtractionService memoryExtractionService,
                            RagConfigLoader configLoader,
                            ObjectMapper objectMapper,
                            RetrievalLogService retrievalLogService,
                            LogEventCollector logEventCollector) {
        this.queryRewriter = queryRewriter;
        this.hydeQueryRewriter = hydeQueryRewriter;
        this.intentClassifier = intentClassifier;
        this.multiChannelRetriever = multiChannelRetriever;
        this.documentReranker = documentReranker;
        this.tokenBudgetTrimmer = tokenBudgetTrimmer;
        this.ragPromptService = ragPromptService;
        this.templateLoader = templateLoader;
        this.routingChatService = routingChatService;
        this.chatModelFactory = chatModelFactory;
        this.traceService = traceService;
        this.memoryExtractionService = memoryExtractionService;
        this.configLoader = configLoader;
        this.objectMapper = objectMapper;
        this.retrievalLogService = retrievalLogService;
        this.logEventCollector = logEventCollector;
    }

    public void execute(RagPipelineContext ctx, SseEmitter emitter) {
        executeInternal(ctx, emitter, null);
    }

    /**
     * 同步执行 Pipeline，返回完整的上下文（不依赖 SseEmitter）。
     *
     * @param ctx          流水线上下文
     * @param experimentId 关联的实验 ID（可为 null）
     * @return 执行后的上下文（包含所有阶段的输出）
     */
    public RagPipelineContext executeSync(RagPipelineContext ctx, Long experimentId) {
        executeInternal(ctx, null, experimentId);
        return ctx;
    }

    private void executeInternal(RagPipelineContext ctx, SseEmitter emitter, Long experimentId) {
        RagConfigDO config = configLoader.getActiveConfig();
        double intentThreshold = getConfigValue(config, RagConfigDO::getIntentConfidenceThreshold, 0.5);
        double highConfidence = getConfigValue(config, RagConfigDO::getIntentConfidenceThreshold, 0.85);
        double rerankThreshold = getConfigValue(config, RagConfigDO::getRerankConfidenceThreshold, 0.5);
        int rerankTopK = getConfigValue(config, RagConfigDO::getRerankTopK, 3);
        int tokenBudget = getConfigValue(config, RagConfigDO::getTokenBudget, 3000);
        double tokenCoeff = getConfigValue(config, RagConfigDO::getTokenEstimateCoefficient, 0.4);
        int docTruncate = getConfigValue(config, RagConfigDO::getRerankDocTruncate, 800);
        int hydeEquivCount = getConfigValue(config, RagConfigDO::getHydeEquivQueryCount, 3);

        // 灰度标签
        String grayTag = GrayContextHolder.get();
        if (grayTag == null) grayTag = "BASELINE";
        ctx.setGrayTag(grayTag);

        String traceId = UUID.randomUUID().toString().replace("-", "");
        String conId = ctx.getConversationId();
        long pipelineStart = System.currentTimeMillis();

        traceService.startRun(traceId, conId, ctx.getOriginalPrompt(), experimentId);
        ctx.setTraceId(traceId);
        // 写入灰度标签到 trace
        try {
            traceService.setGrayTag(traceId, grayTag);
        } catch (Exception e) {
            log.warn("[SmartRagPipeline] 写入 grayTag 失败: {}", e.getMessage());
        }

        try {
            // 1. 查询改写
            RewriteResult rewriteResult = executeRewrite(ctx, traceId, config, tokenCoeff);

            // 2. HyDE 独立通道（生成假设文档 + 等价查询）
            HydeResult hydeResult = executeHyde(rewriteResult.rewrittenQuery, traceId, config, hydeEquivCount);

            IntentResult intentResult = executeClassify(rewriteResult.rewrittenQuery, traceId, config, tokenCoeff, highConfidence);
            ctx.setIntentResult(intentResult);

            String domain = resolveDomain(intentResult);

            if (intentResult.isSystem()) {
                String memory = ctx.isEnableMemory() ? memoryExtractionService.getMemoryContext(ctx.getUserId()) : null;
                String sysPrompt = ragPromptService.build(PromptScene.SYSTEM_CHAT, ctx.getName(), domain, List.of());
                if (memory != null) sysPrompt = memory + "\n" + sysPrompt;
                ctx.setFinalSystemPrompt(sysPrompt);
                traceService.finishRun(traceId, "SUCCESS", null, System.currentTimeMillis() - pipelineStart);
                if (emitter != null) {
                    routingChatService.streamChat(ctx.getOriginalPrompt(), conId, sysPrompt, null, emitter, false, ctx.getUserId(), traceId);
                } else {
                    String answer = executeLlmWithTrace(traceId, ctx.getOriginalPrompt(), conId, sysPrompt, null);
                    ctx.setGeneratedAnswer(answer);
                }
                return;
            }

            Long effectiveKbId = (intentResult.getConfidence() >= intentThreshold) ? intentResult.getKbId() : null;
            // HyDE 独立通道检索：传入假设文档 + 等价查询
            List<Document> retrieved = executeRetrieve(rewriteResult.rewrittenQuery, effectiveKbId, traceId, ctx,
                    config, tokenCoeff, hydeResult.hydeDoc, hydeResult.equivQueries);
            ctx.setRetrievedDocs(retrieved);

            List<Document> reranked = executeRerank(ctx, rewriteResult.rewrittenQuery, retrieved, traceId,
                    config, rerankTopK, rerankThreshold, tokenCoeff, docTruncate);
            ctx.setRerankedDocs(reranked);

            List<Document> trimmed = tokenBudgetTrimmer.trim(reranked, tokenBudget, tokenCoeff);

            String memory = ctx.isEnableMemory() ? memoryExtractionService.getMemoryContext(ctx.getUserId()) : null;
            String finalSystemPrompt = executePromptBuild(ctx, domain, trimmed, memory, traceId);
            ctx.setFinalSystemPrompt(finalSystemPrompt);

            traceService.finishRun(traceId, "SUCCESS", null, System.currentTimeMillis() - pipelineStart);
            if (emitter != null) {
                routingChatService.streamChat(ctx.getOriginalPrompt(), conId, finalSystemPrompt, null, emitter, false, ctx.getUserId(), traceId);
            } else {
                String answer = executeLlmWithTrace(traceId, ctx.getOriginalPrompt(), conId, finalSystemPrompt, null);
                ctx.setGeneratedAnswer(answer);
            }

        } catch (Exception e) {
            log.error("[SmartRagPipeline] traceId={} 流水线异常: {}", traceId, e.getMessage(), e);
            traceService.finishRun(traceId, "ERROR", e.getMessage(), System.currentTimeMillis() - pipelineStart);
            throw e;
        }
    }

    private RewriteResult executeRewrite(RagPipelineContext ctx, String traceId, RagConfigDO config,
                                          double tokenCoeff) {
        long start = System.currentTimeMillis();
        traceService.startNode(traceId, "rewrite", "查询改写", "REWRITE",
                toJson(Map.of("originalPrompt", ctx.getOriginalPrompt(), "enableRewrite", ctx.isEnableRewrite())));

        try {
            String rewrittenQuery = ctx.isEnableRewrite()
                    ? queryRewriter.rewrite(ctx.getOriginalPrompt())
                    : ctx.getOriginalPrompt();

            ctx.setRewrittenQuery(rewrittenQuery);
            int promptTokens = estimateTokens(ctx.getOriginalPrompt(), tokenCoeff);
            int completionTokens = estimateTokens(rewrittenQuery, tokenCoeff);

            traceService.finishNode(traceId, "rewrite", "SUCCESS", null,
                    System.currentTimeMillis() - start,
                    toJson(Map.of("rewrittenQuery", rewrittenQuery)),
                    promptTokens, completionTokens);
            logEventCollector.logEvent("REWRITE_COMPLETED", Map.of(
                    "originalPrompt", ctx.getOriginalPrompt(),
                    "rewrittenQuery", rewrittenQuery,
                    "durationMs", System.currentTimeMillis() - start));
            return new RewriteResult(rewrittenQuery);
        } catch (Exception e) {
            traceService.finishNode(traceId, "rewrite", "ERROR", e.getMessage(),
                    System.currentTimeMillis() - start, null);
            return new RewriteResult(ctx.getOriginalPrompt());
        }
    }

    /**
     * HyDE 独立通道：生成假设文档 + 等价查询，作为独立 trace 节点记录。
     */
    private HydeResult executeHyde(String rewrittenQuery, String traceId, RagConfigDO config, int hydeEquivCount) {
        long start = System.currentTimeMillis();
        traceService.startNode(traceId, "hyde", "HyDE 假设生成", "HYDE",
                toJson(Map.of("query", rewrittenQuery, "enabled", hydeQueryRewriter.shouldUseHyDE(config))));

        try {
            if (!hydeQueryRewriter.shouldUseHyDE(config)) {
                traceService.finishNode(traceId, "hyde", "SUCCESS", null,
                        System.currentTimeMillis() - start,
                        toJson(Map.of("hydeEnabled", false, "skipped", true)),
                        estimateTokens(rewrittenQuery, 0.4), 0);
                return new HydeResult(null, null);
            }

            String hydeDoc = hydeQueryRewriter.generateHypothesisDocument(rewrittenQuery);
            if (hydeDoc != null && hydeDoc.isBlank()) hydeDoc = null;

            List<String> equivQueries = null;
            if (hydeEquivCount > 0) {
                equivQueries = hydeQueryRewriter.generateEquivalentQueries(rewrittenQuery, hydeEquivCount);
            }

            Map<String, Object> output = new LinkedHashMap<>();
            output.put("hydeEnabled", true);
            output.put("hydeDoc", hydeDoc != null ? hydeDoc.substring(0, Math.min(hydeDoc.length(), 200)) + "..." : null);
            output.put("equivQueryCount", equivQueries != null ? equivQueries.size() : 0);
            output.put("equivQueries", equivQueries);

            traceService.finishNode(traceId, "hyde", "SUCCESS", null,
                    System.currentTimeMillis() - start, toJson(output),
                    estimateTokens(rewrittenQuery, 0.4),
                    estimateTokens(hydeDoc, 0.4) + estimateTokens(
                            equivQueries != null ? String.join("", equivQueries) : "", 0.4));
            logEventCollector.logEvent("HYDE_COMPLETED", Map.of(
                    "hydeDocLength", hydeDoc != null ? hydeDoc.length() : 0,
                    "equivQueryCount", equivQueries != null ? equivQueries.size() : 0,
                    "durationMs", System.currentTimeMillis() - start));
            return new HydeResult(hydeDoc, equivQueries);
        } catch (Exception e) {
            log.warn("[HyDE] 假设生成失败，降级跳过: {}", e.getMessage());
            traceService.finishNode(traceId, "hyde", "ERROR", e.getMessage(),
                    System.currentTimeMillis() - start,
                    toJson(Map.of("hydeEnabled", false, "error", e.getMessage())));
            return new HydeResult(null, null);
        }
    }

    private IntentResult executeClassify(String query, String traceId, RagConfigDO config, double tokenCoeff, double highConfidence) {
        long start = System.currentTimeMillis();
        traceService.startNode(traceId, "classify", "意图识别", "CLASSIFY", toJson(Map.of("query", query)));

        try {
            IntentResult result = intentClassifier.classify(query, highConfidence);
            Map<String, Object> output = new HashMap<>();
            output.put("intentNodeId", result.getIntentNodeId());
            output.put("label", result.getLabel());
            output.put("confidence", result.getConfidence());
            output.put("isSystem", result.isSystem());
            output.put("kbId", result.getKbId());
            traceService.finishNode(traceId, "classify", "SUCCESS", null,
                    System.currentTimeMillis() - start, toJson(output),
                    estimateTokens(query, tokenCoeff), estimateTokens(String.valueOf(result.getIntentNodeId()), tokenCoeff));
            logEventCollector.logEvent("INTENT_CLASSIFIED", Map.of(
                    "label", result.getLabel(),
                    "confidence", result.getConfidence(),
                    "kbId", result.getKbId() != null ? result.getKbId() : -1,
                    "isSystem", result.isSystem(),
                    "durationMs", System.currentTimeMillis() - start));
            return result;
        } catch (Exception e) {
            traceService.finishNode(traceId, "classify", "ERROR", e.getMessage(),
                    System.currentTimeMillis() - start, null);
            return IntentResult.unknown();
        }
    }

    private List<Document> executeRetrieve(String query, Long kbId, String traceId,
                                            RagPipelineContext ctx, RagConfigDO config, double tokenCoeff,
                                            String hydeDoc, List<String> equivQueries) {
        long start = System.currentTimeMillis();

        Map<String, Object> retrieveInput = new HashMap<>();
        retrieveInput.put("query", query);
        retrieveInput.put("kbId", kbId);
        retrieveInput.put("hydeEnabled", hydeDoc != null);
        retrieveInput.put("equivQueryCount", equivQueries != null ? equivQueries.size() : 0);
        traceService.startNode(traceId, "retrieve", "多通道检索", "RETRIEVE",
                toJson(retrieveInput));

        try {
            List<Document> docs = multiChannelRetriever.retrieve(query, kbId, config, hydeDoc, equivQueries);
            int latencyMs = (int) (System.currentTimeMillis() - start);

            // 记录检索日志
            if (ctx.getUserId() != null) {
                try {
                    String messageId = UUID.randomUUID().toString();
                    String kbIdStr = kbId != null ? kbId.toString() : null;
                    double avgScore = docs.isEmpty() ? 0.0 :
                            docs.stream().mapToDouble(doc -> {
                                Object score = doc.getMetadata().get("score");
                                return score instanceof Number ? ((Number) score).doubleValue() : 0.0;
                            }).average().orElse(0.0);

                    retrievalLogService.recordRetrieval(
                            ctx.getUserId().toString(),
                            ctx.getConversationId(),
                            messageId,
                            kbIdStr,
                            query,
                            docs.size(),
                            avgScore,
                            latencyMs
                    );
                } catch (Exception e) {
                    log.warn("记录检索日志失败", e);
                }
            }

            traceService.finishNode(traceId, "retrieve", "SUCCESS", null,
                    latencyMs, toJson(buildRetrieveOutput(docs)));
            logEventCollector.logEvent("RETRIEVE_COMPLETED", Map.of(
                    "docCount", docs.size(),
                    "kbId", kbId != null ? kbId : -1,
                    "hydeEnabled", hydeDoc != null,
                    "durationMs", latencyMs));
            return docs;
        } catch (Exception e) {
            traceService.finishNode(traceId, "retrieve", "ERROR", e.getMessage(),
                    System.currentTimeMillis() - start, null);
            return List.of();
        }
    }

    private List<Document> executeRerank(RagPipelineContext ctx, String query,
                                          List<Document> retrieved, String traceId,
                                          RagConfigDO config, int rerankTopK, double rerankThreshold, double tokenCoeff, int docTruncate) {
        long start = System.currentTimeMillis();
        traceService.startNode(traceId, "rerank", "重排序", "RERANK",
                toJson(buildRerankInput(retrieved, ctx.isEnableRerank())));

        try {
            List<Document> finalDocs = ctx.isEnableRerank()
                    ? documentReranker.rerank(query, retrieved, rerankTopK, rerankThreshold, docTruncate)
                    : (retrieved.size() > rerankTopK ? retrieved.subList(0, rerankTopK) : retrieved);

            traceService.finishNode(traceId, "rerank", "SUCCESS", null,
                    System.currentTimeMillis() - start,
                    toJson(buildRerankOutput(retrieved, finalDocs)),
                    estimateTokens(query, tokenCoeff) * retrieved.size(), estimateTokens("score", tokenCoeff) * retrieved.size());
            logEventCollector.logEvent("RERANK_COMPLETED", Map.of(
                    "inputCount", retrieved.size(),
                    "outputCount", finalDocs.size(),
                    "enableRerank", ctx.isEnableRerank(),
                    "durationMs", System.currentTimeMillis() - start));
            return finalDocs;
        } catch (Exception e) {
            traceService.finishNode(traceId, "rerank", "ERROR", e.getMessage(),
                    System.currentTimeMillis() - start, null);
            return retrieved.size() > rerankTopK ? retrieved.subList(0, rerankTopK) : retrieved;
        }
    }

    private String executePromptBuild(RagPipelineContext ctx, String domain,
                                      List<Document> finalDocs, String memory, String traceId) {
        long start = System.currentTimeMillis();
        PromptScene scene = finalDocs.isEmpty() ? PromptScene.EMPTY_RETRIEVAL : PromptScene.KB_ONLY;
        String templatePath = scene == PromptScene.KB_ONLY
                ? RAGPromptService.RAG_KB_PROMPT_PATH
                : RAGPromptService.SYSTEM_CHAT_PROMPT_PATH;
        String rawTemplate = templateLoader.load(templatePath);

        traceService.startNode(traceId, "prompt", "Prompt组装", "PROMPT",
                toJson(buildPromptInput(domain, finalDocs, memory, rawTemplate)));

        String prompt = ragPromptService.build(scene, ctx.getName(), domain, finalDocs);
        if (memory != null && !memory.isBlank()) {
            prompt = memory + "\n" + prompt;
        }

        traceService.finishNode(traceId, "prompt", "SUCCESS", null,
                System.currentTimeMillis() - start,
                toJson(Map.of("scene", scene.name(), "promptLength", prompt.length(), "fullPrompt", prompt)));
        logEventCollector.logEvent("PROMPT_BUILT", Map.of(
                "scene", scene.name(),
                "docCount", finalDocs.size(),
                "promptLength", prompt.length(),
                "domain", domain,
                "durationMs", System.currentTimeMillis() - start));
        return prompt;
    }

    /**
     * 执行 LLM 调用并记录 Trace 节点。
     * 数据结构与 RoutingChatService.tryStreamWithEntry 保持一致，
     * 前端 TraceDetailPage 按 modelId/systemPrompt/userPrompt（输入）和
     * modelId/response/inputTokens/outputTokens（输出）解析。
     */
    private String executeLlmWithTrace(String traceId, String originalPrompt, String conversationId,
                                        String systemPrompt, Long userId) {
        long start = System.currentTimeMillis();
        String modelId = chatModelFactory.getCandidates().isEmpty()
                ? "unknown" : chatModelFactory.getCandidates().get(0).id();

        // 输入数据：与 RoutingChatService.buildLlmInputData 一致
        Map<String, Object> inputData = new LinkedHashMap<>();
        inputData.put("modelId", modelId);
        inputData.put("systemPrompt", truncate(systemPrompt, 2000));
        inputData.put("userPrompt", truncate(originalPrompt, 500));
        traceService.startNode(traceId, "llm", "增强生成", "LLM", toJson(inputData));

        try {
            String answer = routingChatService.chat(originalPrompt, conversationId, systemPrompt, null);

            int inputTokens = estimateTokens(systemPrompt, 0.4);
            int outputTokens = estimateTokens(answer, 0.4);

            // 输出数据：与 RoutingChatService.buildLlmOutputData 一致
            Map<String, Object> outputData = new LinkedHashMap<>();
            outputData.put("modelId", modelId);
            outputData.put("response", truncate(answer, 3000));
            outputData.put("inputTokens", inputTokens);
            outputData.put("outputTokens", outputTokens);
            traceService.finishNode(traceId, "llm", "SUCCESS", null,
                    System.currentTimeMillis() - start, toJson(outputData),
                    inputTokens, outputTokens);
            logEventCollector.logEvent("LLM_COMPLETED", Map.of(
                    "answerLength", answer != null ? answer.length() : 0,
                    "durationMs", System.currentTimeMillis() - start));
            return answer;
        } catch (Exception e) {
            log.error("[SmartRagPipeline] LLM 调用失败: {}", e.getMessage());
            traceService.finishNode(traceId, "llm", "ERROR", e.getMessage(),
                    System.currentTimeMillis() - start, null);
            return null;
        }
    }

    private String resolveDomain(IntentResult intentResult) {
        if (intentResult == null) return "各领域";
        if (intentResult.isSystem() || intentResult.getConfidence() == 0.0) return "各领域";
        return intentResult.getLabel();
    }

    /**
     * 构建检索阶段输出数据（含每篇文档摘要）。
     */
    private Map<String, Object> buildRetrieveOutput(List<Document> docs) {
        Map<String, Object> result = new HashMap<>();
        result.put("count", docs.size());
        List<Map<String, Object>> docList = docs.stream()
                .limit(10)
                .map(doc -> {
                    Map<String, Object> docInfo = new HashMap<>();
                    docInfo.put("docId", doc.getId());
                    Object knowledgeDocId = doc.getMetadata().get("doc_id");
                    docInfo.put("knowledgeDocId", knowledgeDocId != null ? String.valueOf(knowledgeDocId) : null);
                    docInfo.put("content", truncate(doc.getFormattedContent(), 500));
                    Object score = doc.getMetadata().get("score");
                    docInfo.put("score", score != null ? score : null);
                    Object kbId = doc.getMetadata().get("kb_id");
                    docInfo.put("kbId", kbId != null ? kbId : null);
                    Object source = doc.getMetadata().get("source");
                    docInfo.put("source", source != null ? source : "vector");
                    return docInfo;
                })
                .collect(Collectors.toList());
        result.put("docs", docList);
        return result;
    }

    /**
     * 构建重排序阶段输入数据（含每篇待排序文档）。
     */
    private Map<String, Object> buildRerankInput(List<Document> docs, boolean enableRerank) {
        Map<String, Object> result = new HashMap<>();
        result.put("inputCount", docs.size());
        result.put("enableRerank", enableRerank);
        List<Map<String, Object>> docList = docs.stream()
                .limit(20)
                .map(doc -> {
                    Map<String, Object> docInfo = new HashMap<>();
                    docInfo.put("content", truncate(doc.getFormattedContent(), 300));
                    Object kbId = doc.getMetadata().get("kb_id");
                    docInfo.put("kbId", kbId != null ? kbId : null);
                    return docInfo;
                })
                .collect(Collectors.toList());
        result.put("docs", docList);
        return result;
    }

    /**
     * 构建重排序阶段输出数据（含每篇排序后文档及分数）。
     */
    private Map<String, Object> buildRerankOutput(List<Document> original, List<Document> finalDocs) {
        Map<String, Object> result = new HashMap<>();
        result.put("inputCount", original.size());
        result.put("count", finalDocs.size());
        List<Map<String, Object>> docList = finalDocs.stream()
                .limit(10)
                .map(doc -> {
                    Map<String, Object> docInfo = new HashMap<>();
                    docInfo.put("docId", doc.getId());
                    Object knowledgeDocId = doc.getMetadata().get("doc_id");
                    docInfo.put("knowledgeDocId", knowledgeDocId != null ? String.valueOf(knowledgeDocId) : null);
                    docInfo.put("content", truncate(doc.getFormattedContent(), 300));
                    Object rerankScore = doc.getMetadata().get("rerank_score");
                    docInfo.put("score", rerankScore != null ? rerankScore : null);
                    Object kbId = doc.getMetadata().get("kb_id");
                    docInfo.put("kbId", kbId != null ? kbId : null);
                    return docInfo;
                })
                .collect(Collectors.toList());
        result.put("docs", docList);
        return result;
    }

    /**
     * 构建 Prompt 组装阶段输入数据（领域、模板内容、记忆上下文、文档内容）。
     */
    private Map<String, Object> buildPromptInput(String domain, List<Document> docs, String memory, String rawTemplate) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("domain", domain);
        input.put("docCount", docs.size());
        input.put("rawTemplate", rawTemplate != null ? rawTemplate : null);
        input.put("memoryContext", memory != null && !memory.isBlank() ? memory : null);
        // 完整文档内容
        List<Map<String, Object>> docList = docs.stream()
                .limit(20)
                .map(doc -> {
                    Map<String, Object> docInfo = new LinkedHashMap<>();
                    docInfo.put("content", doc.getFormattedContent());
                    Object score = doc.getMetadata().get("rerank_score");
                    if (score == null) score = doc.getMetadata().get("score");
                    docInfo.put("score", score != null ? score : null);
                    Object kbId = doc.getMetadata().get("kb_id");
                    docInfo.put("kbId", kbId != null ? kbId : null);
                    return docInfo;
                })
                .collect(Collectors.toList());
        input.put("docs", docList);
        return input;
    }

    /**
     * 截断字符串到指定长度。
     */
    private String truncate(String text, int maxLen) {
        if (text == null) return null;
        if (text.length() <= maxLen) return text;
        return text.substring(0, maxLen) + "...";
    }

    private String toJson(Object obj) {
        try { return objectMapper.writeValueAsString(obj); }
        catch (JsonProcessingException e) { return "{}"; }
    }

    private int estimateTokens(String text, double coefficient) {
        if (text == null || text.isBlank()) return 0;
        return (int) Math.ceil(text.length() * coefficient);
    }

    /**
     * 从配置中安全读取数值，配置为空时使用默认值。
     */
    private double getConfigValue(RagConfigDO config, java.util.function.Function<RagConfigDO, Double> getter, double defaultValue) {
        if (config == null) return defaultValue;
        Double value = getter.apply(config);
        return value != null ? value : defaultValue;
    }

    private int getConfigValue(RagConfigDO config, java.util.function.Function<RagConfigDO, Integer> getter, int defaultValue) {
        if (config == null) return defaultValue;
        Integer value = getter.apply(config);
        return value != null ? value : defaultValue;
    }

    /**
     * 改写结果（仅含改写后的查询）。
     */
    private record RewriteResult(String rewrittenQuery) {}

    /**
     * HyDE 结果（假设文档 + 等价查询）。
     */
    private record HydeResult(String hydeDoc, List<String> equivQueries) {}
}