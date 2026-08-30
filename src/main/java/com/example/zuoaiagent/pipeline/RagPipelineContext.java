package com.example.zuoaiagent.pipeline;

import com.example.zuoaiagent.intent.model.IntentResult;
import org.springframework.ai.document.Document;

import java.util.List;

/**
 * RAG 流水线上下文对象
 *
 * <p>贯穿 SmartRagPipeline 各阶段的数据载体，各阶段读写此对象。
 */
public class RagPipelineContext {

    /** 用户原始问题 */
    private final String originalPrompt;

    /** 会话 ID */
    private final String conversationId;

    /** AI 助手名称 */
    private final String name;

    /** 是否启用问题改写 */
    private final boolean enableRewrite;

    /** 是否启用重排序 */
    private final boolean enableRerank;

    /** 是否启用长期记忆注入 */
    private final boolean enableMemory;

    /** 当前用户 ID（用于长期记忆查询） */
    private final Long userId;

    /** 改写后的查询（阶段②产出） */
    private String rewrittenQuery;

    /** 意图分类结果（阶段③产出） */
    private IntentResult intentResult;

    /** 多通道检索结果（阶段⑤产出） */
    private List<Document> retrievedDocs;

    /** 重排序后文档（阶段⑥产出） */
    private List<Document> rerankedDocs;

    /** 最终注入 LLM 的系统提示词（阶段⑦产出） */
    private String finalSystemPrompt;

    /** 链路追踪 ID（流水线开始时生成） */
    private String traceId;

public RagPipelineContext(String originalPrompt, String conversationId,
                               String name,
                               boolean enableRewrite, boolean enableRerank,
                               boolean enableMemory, Long userId) {
        this.originalPrompt = originalPrompt;
        this.conversationId = conversationId;
        this.name = name;
        this.enableRewrite = enableRewrite;
        this.enableRerank = enableRerank;
        this.enableMemory = enableMemory;
        this.userId = userId;
    }

    public String getOriginalPrompt() { return originalPrompt; }
    public String getConversationId() { return conversationId; }
    public String getName() { return name; }
    public boolean isEnableRewrite() { return enableRewrite; }
    public boolean isEnableRerank() { return enableRerank; }
    public boolean isEnableMemory() { return enableMemory; }
    public Long getUserId() { return userId; }

    public String getRewrittenQuery() { return rewrittenQuery; }
    public void setRewrittenQuery(String rewrittenQuery) { this.rewrittenQuery = rewrittenQuery; }

    public IntentResult getIntentResult() { return intentResult; }
    public void setIntentResult(IntentResult intentResult) { this.intentResult = intentResult; }

    public List<Document> getRetrievedDocs() { return retrievedDocs; }
    public void setRetrievedDocs(List<Document> retrievedDocs) { this.retrievedDocs = retrievedDocs; }

    public List<Document> getRerankedDocs() { return rerankedDocs; }
    public void setRerankedDocs(List<Document> rerankedDocs) { this.rerankedDocs = rerankedDocs; }

    public String getFinalSystemPrompt() { return finalSystemPrompt; }
    public void setFinalSystemPrompt(String finalSystemPrompt) { this.finalSystemPrompt = finalSystemPrompt; }

    public String getTraceId() { return traceId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }

    /** 获取实际用于检索的查询（改写后或原始） */
    public String getEffectiveQuery() {
        return rewrittenQuery != null && !rewrittenQuery.isBlank() ? rewrittenQuery : originalPrompt;
    }
}
