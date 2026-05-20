package com.example.zuoaiagent.pipeline;

import com.example.zuoaiagent.chat.RoutingChatService;
import com.example.zuoaiagent.intent.model.IntentResult;
import com.example.zuoaiagent.intent.service.IntentClassifier;
import com.example.zuoaiagent.prompt.PromptScene;
import com.example.zuoaiagent.prompt.RAGPromptService;
import com.example.zuoaiagent.rag.DocumentReranker;
import com.example.zuoaiagent.rag.MultiChannelRetriever;
import com.example.zuoaiagent.rag.QueryRewriter;
import com.example.zuoaiagent.trace.service.RagTraceRecordService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 智能 RAG 流水线（含 Prompt 组装 + 链路追踪）
 *
 * <p>执行步骤：
 * <ol>
 *   <li>改写查询（可选）</li>
 *   <li>意图分类（LLM）</li>
 *   <li>短路处理（系统/闲聊节点：直接 LLM 流式输出）</li>
 *   <li>多通道并行检索（全局 + 意图定向）</li>
 *   <li>重排序（可选）</li>
 *   <li>Prompt 组装（PromptScene 选模板 + ContextFormatter 格式化文档）</li>
 *   <li>路由流式推送</li>
 * </ol>
 *
 * <p>每个步骤均通过 {@link RagTraceRecordService} 异步记录耗时和数据到数据库。
 */
@Component
public class SmartRagPipeline {

    private static final Logger log = LoggerFactory.getLogger(SmartRagPipeline.class);

    /** 意图分类置信度阈值：低于此值时 kbId 不传入定向通道 */
    private static final double CONFIDENCE_THRESHOLD = 0.5;

    private final QueryRewriter queryRewriter;
    private final IntentClassifier intentClassifier;
    private final MultiChannelRetriever multiChannelRetriever;
    private final DocumentReranker documentReranker;
    private final RAGPromptService ragPromptService;
    private final RoutingChatService routingChatService;
    private final RagTraceRecordService traceService;
    private final ObjectMapper objectMapper;

    public SmartRagPipeline(QueryRewriter queryRewriter,
                            IntentClassifier intentClassifier,
                            MultiChannelRetriever multiChannelRetriever,
                            DocumentReranker documentReranker,
                            RAGPromptService ragPromptService,
                            RoutingChatService routingChatService,
                            RagTraceRecordService traceService,
                            ObjectMapper objectMapper) {
        this.queryRewriter = queryRewriter;
        this.intentClassifier = intentClassifier;
        this.multiChannelRetriever = multiChannelRetriever;
        this.documentReranker = documentReranker;
        this.ragPromptService = ragPromptService;
        this.routingChatService = routingChatService;
        this.traceService = traceService;
        this.objectMapper = objectMapper;
    }

    /**
     * 执行智能 RAG 流水线，结果通过 SSE 流式推送。
     */
    public void execute(RagPipelineContext ctx, SseEmitter emitter) {
        String traceId = UUID.randomUUID().toString().replace("-", "");
        String conId = ctx.getConversationId();
        long pipelineStart = System.currentTimeMillis();

        // ── 流水线开始 ──
        traceService.startRun(traceId, conId, ctx.getOriginalPrompt());
        ctx.setTraceId(traceId);

        try {
            // ① 改写查询
            String rewrittenQuery = executeRewrite(ctx, traceId);

            // ② 意图分类
            IntentResult intentResult = executeClassify(rewrittenQuery, traceId);
            ctx.setIntentResult(intentResult);

            String domain = resolveDomain(intentResult);

            // ③ 短路：系统/闲聊节点
            if (intentResult.isSystem()) {
                log.info("[SmartRagPipeline] traceId={} 命中系统节点，短路直接回复", traceId);
                String sysPrompt = ragPromptService.build(PromptScene.SYSTEM_CHAT, ctx.getName(), domain, List.of());
                ctx.setFinalSystemPrompt(sysPrompt);
                traceService.finishRun(traceId, "SUCCESS", null, System.currentTimeMillis() - pipelineStart);
                routingChatService.streamChat(ctx.getOriginalPrompt(), conId, sysPrompt, null, emitter);
                return;
            }

            // ④ 多通道并行检索
            List<Document> retrieved = executeRetrieve(rewrittenQuery, intentResult, traceId);
            ctx.setRetrievedDocs(retrieved);

            // ⑤ 重排序
            List<Document> finalDocs = executeRerank(ctx, rewrittenQuery, retrieved, traceId);
            ctx.setRerankedDocs(finalDocs);

            // ⑥ Prompt 组装
            String finalSystemPrompt = executePromptBuild(ctx, domain, finalDocs, traceId);
            ctx.setFinalSystemPrompt(finalSystemPrompt);

            // ── 流水线成功 ──
            traceService.finishRun(traceId, "SUCCESS", null, System.currentTimeMillis() - pipelineStart);

            // ⑦ 路由流式推送
            routingChatService.streamChat(ctx.getOriginalPrompt(), conId, finalSystemPrompt, null, emitter);

        } catch (Exception e) {
            log.error("[SmartRagPipeline] traceId={} 流水线异常: {}", traceId, e.getMessage(), e);
            traceService.finishRun(traceId, "ERROR", e.getMessage(), System.currentTimeMillis() - pipelineStart);
            throw e;
        }
    }

    // ==================== 各阶段私有方法 ====================

    /** ① 改写查询 */
    private String executeRewrite(RagPipelineContext ctx, String traceId) {
        long start = System.currentTimeMillis();
        String inputJson = toJson(Map.of("originalPrompt", ctx.getOriginalPrompt(),
                "enableRewrite", ctx.isEnableRewrite()));
        traceService.startNode(traceId, "rewrite", "查询改写", "REWRITE", inputJson);

        String rewrittenQuery;
        try {
            rewrittenQuery = ctx.isEnableRewrite()
                    ? queryRewriter.rewrite(ctx.getOriginalPrompt())
                    : ctx.getOriginalPrompt();
            ctx.setRewrittenQuery(rewrittenQuery);

            String outputJson = toJson(Map.of("rewrittenQuery", rewrittenQuery));
            traceService.finishNode(traceId, "rewrite", "SUCCESS", null,
                    System.currentTimeMillis() - start, outputJson);

            log.info("[SmartRagPipeline] traceId={} 改写: {} → {}", traceId, ctx.getOriginalPrompt(), rewrittenQuery);
            return rewrittenQuery;
        } catch (Exception e) {
            traceService.finishNode(traceId, "rewrite", "ERROR", e.getMessage(),
                    System.currentTimeMillis() - start, null);
            log.warn("[SmartRagPipeline] 改写失败，降级使用原始 prompt: {}", e.getMessage());
            return ctx.getOriginalPrompt();
        }
    }

    /** ② 意图分类 */
    private IntentResult executeClassify(String query, String traceId) {
        long start = System.currentTimeMillis();
        String inputJson = toJson(Map.of("query", query));
        traceService.startNode(traceId, "classify", "意图分类", "CLASSIFY", inputJson);

        try {
            IntentResult result = intentClassifier.classify(query);

            Map<String, Object> output = new HashMap<>();
            output.put("intentNodeId", result.getIntentNodeId());
            output.put("label", result.getLabel());
            output.put("confidence", result.getConfidence());
            output.put("isSystem", result.isSystem());
            output.put("kbId", result.getKbId());
            traceService.finishNode(traceId, "classify", "SUCCESS", null,
                    System.currentTimeMillis() - start, toJson(output));

            log.info("[SmartRagPipeline] traceId={} 意图: {}", traceId, result);
            return result;
        } catch (Exception e) {
            traceService.finishNode(traceId, "classify", "ERROR", e.getMessage(),
                    System.currentTimeMillis() - start, null);
            log.error("[SmartRagPipeline] 意图分类失败，降级为 unknown: {}", e.getMessage());
            return IntentResult.unknown();
        }
    }

    /** ④ 多通道并行检索 */
    private List<Document> executeRetrieve(String query, IntentResult intentResult, String traceId) {
        long start = System.currentTimeMillis();
        Long kbId = (intentResult.getConfidence() >= CONFIDENCE_THRESHOLD) ? intentResult.getKbId() : null;

        Map<String, Object> input = new HashMap<>();
        input.put("query", query);
        input.put("kbId", kbId);
        traceService.startNode(traceId, "retrieve", "多通道检索", "RETRIEVE", toJson(input));

        try {
            List<Document> docs = multiChannelRetriever.retrieve(query, kbId);

            // 输出记录：文档数量 + 每个文档的 id 和前 200 字
            List<Map<String, Object>> docSummaries = docs.stream()
                    .map(d -> {
                        Map<String, Object> m = new HashMap<>();
                        m.put("id", d.getId());
                        String text = d.getFormattedContent();
                        m.put("preview", text != null && text.length() > 200 ? text.substring(0, 200) + "..." : text);
                        return m;
                    })
                    .collect(Collectors.toList());

            Map<String, Object> output = new HashMap<>();
            output.put("count", docs.size());
            output.put("docs", docSummaries);
            traceService.finishNode(traceId, "retrieve", "SUCCESS", null,
                    System.currentTimeMillis() - start, toJson(output));

            log.info("[SmartRagPipeline] traceId={} 检索到 {} 个文档片段", traceId, docs.size());
            return docs;
        } catch (Exception e) {
            traceService.finishNode(traceId, "retrieve", "ERROR", e.getMessage(),
                    System.currentTimeMillis() - start, null);
            log.error("[SmartRagPipeline] 检索失败: {}", e.getMessage());
            return List.of();
        }
    }

    /** ⑤ 重排序 */
    private List<Document> executeRerank(RagPipelineContext ctx, String query,
                                         List<Document> retrieved, String traceId) {
        long start = System.currentTimeMillis();
        traceService.startNode(traceId, "rerank", "重排序", "RERANK",
                toJson(Map.of("inputCount", retrieved.size(), "enableRerank", ctx.isEnableRerank())));

        try {
            List<Document> finalDocs = ctx.isEnableRerank()
                    ? documentReranker.rerank(query, retrieved, 3)
                    : (retrieved.size() > 3 ? retrieved.subList(0, 3) : retrieved);

            // 输出记录：保留文档数量 + 完整文本（供后续回查）
            List<Map<String, Object>> docDetails = finalDocs.stream()
                    .map(d -> {
                        Map<String, Object> m = new HashMap<>();
                        m.put("id", d.getId());
                        m.put("content", d.getFormattedContent());
                        return m;
                    })
                    .collect(Collectors.toList());

            Map<String, Object> output = new HashMap<>();
            output.put("count", finalDocs.size());
            output.put("docs", docDetails);
            traceService.finishNode(traceId, "rerank", "SUCCESS", null,
                    System.currentTimeMillis() - start, toJson(output));

            log.info("[SmartRagPipeline] traceId={} 重排序后保留 {} 个文档片段", traceId, finalDocs.size());
            return finalDocs;
        } catch (Exception e) {
            traceService.finishNode(traceId, "rerank", "ERROR", e.getMessage(),
                    System.currentTimeMillis() - start, null);
            log.warn("[SmartRagPipeline] 重排序失败，使用原始检索结果: {}", e.getMessage());
            return retrieved.size() > 3 ? retrieved.subList(0, 3) : retrieved;
        }
    }

    /** ⑥ Prompt 组装 */
    private String executePromptBuild(RagPipelineContext ctx, String domain,
                                      List<Document> finalDocs, String traceId) {
        long start = System.currentTimeMillis();
        traceService.startNode(traceId, "prompt", "Prompt组装", "PROMPT",
                toJson(Map.of("scene", finalDocs.isEmpty() ? "EMPTY_RETRIEVAL" : "KB_ONLY",
                        "domain", domain, "docCount", finalDocs.size())));

        PromptScene scene = finalDocs.isEmpty() ? PromptScene.EMPTY_RETRIEVAL : PromptScene.KB_ONLY;
        String finalSystemPrompt = ragPromptService.build(scene, ctx.getName(), domain, finalDocs);

        traceService.finishNode(traceId, "prompt", "SUCCESS", null,
                System.currentTimeMillis() - start,
                toJson(Map.of("scene", scene.name(), "promptLength", finalSystemPrompt.length())));

        log.info("[SmartRagPipeline] traceId={} Prompt 组装完成 scene={} 长度={}",
                traceId, scene, finalSystemPrompt.length());
        return finalSystemPrompt;
    }

    // ==================== 工具方法 ====================

    private String resolveDomain(IntentResult intentResult) {
        if (intentResult == null) return "各领域";
        if (intentResult.isSystem() || intentResult.getConfidence() == 0.0) return "各领域";
        return intentResult.getLabel();
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }
}
