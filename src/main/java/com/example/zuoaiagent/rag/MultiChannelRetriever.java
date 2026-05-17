package com.example.zuoaiagent.rag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

/**
 * 多通道并行检索器
 *
 * <p>双通道并行执行：
 * <ul>
 *   <li>全局通道（globalSearch）：在全量向量空间中检索，覆盖面广</li>
 *   <li>意图定向通道（intentDirectedSearch）：按知识库 ID 过滤，精度高</li>
 * </ul>
 *
 * <p>两路结果合并后去重（按 document id），意图定向结果优先排在前面。
 */
@Component
public class MultiChannelRetriever {

    private static final Logger log = LoggerFactory.getLogger(MultiChannelRetriever.class);

    private static final int DEFAULT_TOP_K = 6;
    private static final long RETRIEVE_TIMEOUT_SEC = 10L;

    private final VectorStore vectorStore;
    private final Executor retrievalExecutor;

    public MultiChannelRetriever(VectorStore vectorStore,
                                 @Qualifier("retrievalExecutor") Executor retrievalExecutor) {
        this.vectorStore = vectorStore;
        this.retrievalExecutor = retrievalExecutor;
    }

    /**
     * 双通道并行检索，合并去重后返回。
     *
     * @param query      检索查询（建议使用改写后的查询）
     * @param kbId       意图定向的知识库 ID（为 null 时只走全局通道）
     * @param topK       每个通道检索的最大条数
     * @return 合并去重后的文档列表，意图定向结果在前
     */
    public List<Document> retrieve(String query, Long kbId, int topK) {
        CompletableFuture<List<Document>> globalFuture = CompletableFuture.supplyAsync(
                () -> globalSearch(query, topK), retrievalExecutor);

        CompletableFuture<List<Document>> intentFuture = (kbId != null)
                ? CompletableFuture.supplyAsync(() -> intentDirectedSearch(query, kbId, topK), retrievalExecutor)
                : CompletableFuture.completedFuture(List.of());

        try {
            List<Document> globalDocs = globalFuture.get(RETRIEVE_TIMEOUT_SEC, TimeUnit.SECONDS);
            List<Document> intentDocs = intentFuture.get(RETRIEVE_TIMEOUT_SEC, TimeUnit.SECONDS);
            List<Document> merged = merge(intentDocs, globalDocs);
            log.info("[MultiChannelRetriever] query={} kbId={} 全局:{} 定向:{} 合并:{}",
                    query, kbId, globalDocs.size(), intentDocs.size(), merged.size());
            return merged;
        } catch (Exception e) {
            log.warn("[MultiChannelRetriever] 并行检索超时或异常，降级为全局检索: {}", e.getMessage());
            globalFuture.cancel(true);
            intentFuture.cancel(true);
            return globalSearch(query, topK);
        }
    }

    /**
     * 使用默认 topK 检索。
     */
    public List<Document> retrieve(String query, Long kbId) {
        return retrieve(query, kbId, DEFAULT_TOP_K);
    }

    // -------------------- 私有方法 --------------------

    private List<Document> globalSearch(String query, int topK) {
        try {
            return vectorStore.similaritySearch(
                    SearchRequest.builder().query(query).topK(topK).build());
        } catch (Exception e) {
            log.warn("[MultiChannelRetriever] 全局检索失败: {}", e.getMessage());
            return List.of();
        }
    }

    private List<Document> intentDirectedSearch(String query, Long kbId, int topK) {
        try {
            FilterExpressionBuilder b = new FilterExpressionBuilder();
            return vectorStore.similaritySearch(
                    SearchRequest.builder()
                            .query(query)
                            .topK(topK)
                            .filterExpression(b.eq("kb_id", kbId.toString()).build())
                            .build());
        } catch (Exception e) {
            log.warn("[MultiChannelRetriever] 意图定向检索失败 kbId={}: {}", kbId, e.getMessage());
            return List.of();
        }
    }

    /** 合并两路结果，priority 在前，按 document id 去重 */
    private List<Document> merge(List<Document> priority, List<Document> secondary) {
        List<Document> result = new ArrayList<>(priority);
        Set<String> seen = new HashSet<>();
        for (Document doc : priority) {
            seen.add(doc.getId());
        }
        for (Document doc : secondary) {
            if (seen.add(doc.getId())) {
                result.add(doc);
            }
        }
        return result;
    }
}
