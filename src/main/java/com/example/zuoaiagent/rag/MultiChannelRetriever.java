package com.example.zuoaiagent.rag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

@Component
public class MultiChannelRetriever {

    private static final Logger log = LoggerFactory.getLogger(MultiChannelRetriever.class);

    private static final int DEFAULT_TOP_K = 6;
    private static final long RETRIEVE_TIMEOUT_SEC = 10L;
    private static final double RRF_K = 60.0;

    private final VectorStore vectorStore;
    private final Executor retrievalExecutor;
    private final JdbcTemplate jdbcTemplate;

    public MultiChannelRetriever(VectorStore vectorStore,
                                 @Qualifier("retrievalExecutor") Executor retrievalExecutor,
                                 JdbcTemplate jdbcTemplate) {
        this.vectorStore = vectorStore;
        this.retrievalExecutor = retrievalExecutor;
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Document> retrieve(String query, Long kbId, int topK) {
        CompletableFuture<List<Document>> globalFuture = CompletableFuture.supplyAsync(
                () -> globalSearch(query, topK), retrievalExecutor);

        CompletableFuture<List<Document>> intentFuture = (kbId != null)
                ? CompletableFuture.supplyAsync(() -> intentDirectedSearch(query, kbId, topK), retrievalExecutor)
                : CompletableFuture.completedFuture(List.of());

        CompletableFuture<List<Document>> fulltextFuture = CompletableFuture.supplyAsync(
                () -> fullTextSearch(query, kbId, topK), retrievalExecutor);

        try {
            List<Document> globalDocs = globalFuture.get(RETRIEVE_TIMEOUT_SEC, TimeUnit.SECONDS);
            List<Document> intentDocs = intentFuture.get(RETRIEVE_TIMEOUT_SEC, TimeUnit.SECONDS);
            List<Document> fulltextDocs = fulltextFuture.get(RETRIEVE_TIMEOUT_SEC, TimeUnit.SECONDS);

            List<Document> merged = rrfFusion(List.of(
                    new ChannelResult("vector_global", globalDocs),
                    new ChannelResult("vector_intent", intentDocs),
                    new ChannelResult("fulltext", fulltextDocs)
            ), topK);

            log.info("[MultiChannelRetriever] 向量全局:{} 向量定向:{} 全文:{} RRF合并:{}",
                    globalDocs.size(), intentDocs.size(), fulltextDocs.size(), merged.size());
            return merged;
        } catch (Exception e) {
            log.warn("[MultiChannelRetriever] 并行检索异常，降级: {}", e.getMessage());
            return rrfFusion(List.of(new ChannelResult("vector_global", globalSearch(query, topK))), topK);
        }
    }

    public List<Document> retrieve(String query, Long kbId) {
        return retrieve(query, kbId, DEFAULT_TOP_K);
    }

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

    private List<Document> fullTextSearch(String query, Long kbId, int topK) {
        try {
            String sql = "SELECT d.id, d.doc_name, d.content, d.kb_id, " +
                    "ts_rank(d.search_vector, plainto_tsquery('simple', ?)) as rank " +
                    "FROM t_knowledge_document d " +
                    "WHERE d.deleted = 0 AND d.search_vector @@ plainto_tsquery('simple', ?) ";
            List<Object> params = new ArrayList<>();
            params.add(query.replaceAll("\\s+", " & "));
            params.add(query.replaceAll("\\s+", " & "));

            if (kbId != null) {
                sql += " AND d.kb_id = ? ";
                params.add(kbId);
            }
            sql += " ORDER BY rank DESC LIMIT ? ";
            params.add(topK);

            List<Document> docs = new ArrayList<>();
            jdbcTemplate.query(sql, params.toArray(), rs -> {
                Map<String, Object> meta = new HashMap<>();
                meta.put("kb_id", rs.getString("kb_id"));
                meta.put("doc_name", rs.getString("doc_name"));
                meta.put("source", "fulltext");
                docs.add(new Document(rs.getString("content"), meta));
            });
            return docs;
        } catch (Exception e) {
            log.debug("[MultiChannelRetriever] 全文检索失败(可能search_vector列不存在): {}", e.getMessage());
            return List.of();
        }
    }

    private List<Document> rrfFusion(List<ChannelResult> channels, int topK) {
        Map<String, Double> scores = new HashMap<>();
        Map<String, Document> docMap = new LinkedHashMap<>();

        for (ChannelResult channel : channels) {
            for (int i = 0; i < channel.docs.size(); i++) {
                Document doc = channel.docs.get(i);
                String id = doc.getId();
                if (id == null || id.isBlank()) {
                    id = UUID.randomUUID().toString();
                }
                docMap.putIfAbsent(id, doc);
                scores.merge(id, 1.0 / (RRF_K + i + 1), Double::sum);
            }
        }

        return docMap.entrySet().stream()
                .sorted((a, b) -> Double.compare(scores.get(b.getKey()), scores.get(a.getKey())))
                .limit(topK)
                .map(e -> docMap.get(e.getKey()))
                .toList();
    }

    private record ChannelResult(String name, List<Document> docs) {}
}