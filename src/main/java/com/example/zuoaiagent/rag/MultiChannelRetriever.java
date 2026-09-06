package com.example.zuoaiagent.rag;

import com.example.zuoaiagent.log.LogTransaction;
import com.example.zuoaiagent.raglab.entity.RagConfigDO;
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

    /**
     * 多通道检索（支持 HyDE 独立通道 + 等价查询通道）。
     *
     * @param query           改写后的查询
     * @param kbId            目标知识库 ID（可为 null）
     * @param config          RAG 配置（含 HyDE 开关/等价查询数等）
     * @param hydeDoc         HyDE 假设文档（可为 null，未启用 HyDE 时传 null）
     * @param equivQueries    等价查询列表（可为 null 或空列表）
     * @return 融合后的文档列表
     */
    @LogTransaction(name = "多通道检索", eventType = "RETRIEVE", logOutput = false, maxOutputLength = 200)
    public List<Document> retrieve(String query, Long kbId, RagConfigDO config,
                                    String hydeDoc, List<String> equivQueries) {
        int topK = (config != null && config.getRetrieveGlobalTopK() != null)
                ? config.getRetrieveGlobalTopK() : DEFAULT_TOP_K;
        double rrfK = (config != null && config.getRrfK() != null)
                ? config.getRrfK() : RRF_K;
        long timeoutSec = (config != null && config.getRetrieveTimeoutSec() != null)
                ? config.getRetrieveTimeoutSec() : RETRIEVE_TIMEOUT_SEC;

        int globalTopK = (config != null && config.getRetrieveGlobalTopK() != null)
                ? config.getRetrieveGlobalTopK() : DEFAULT_TOP_K;
        int intentTopK = (config != null && config.getRetrieveIntentTopK() != null)
                ? config.getRetrieveIntentTopK() : DEFAULT_TOP_K;
        int fulltextTopK = (config != null && config.getRetrieveFulltextTopK() != null)
                ? config.getRetrieveFulltextTopK() : DEFAULT_TOP_K;

        // 基础 3 通道
        CompletableFuture<List<Document>> globalFuture = CompletableFuture.supplyAsync(
                () -> globalSearch(query, globalTopK), retrievalExecutor);

        CompletableFuture<List<Document>> intentFuture = (kbId != null)
                ? CompletableFuture.supplyAsync(() -> intentDirectedSearch(query, kbId, intentTopK), retrievalExecutor)
                : CompletableFuture.completedFuture(List.of());

        CompletableFuture<List<Document>> fulltextFuture = CompletableFuture.supplyAsync(
                () -> fullTextSearch(query, kbId, fulltextTopK), retrievalExecutor);

        // HyDE 假设文档通道（条件触发）
        CompletableFuture<List<Document>> hydeGlobalFuture;
        CompletableFuture<List<Document>> hydeIntentFuture;
        if (hydeDoc != null && !hydeDoc.isBlank()) {
            hydeGlobalFuture = CompletableFuture.supplyAsync(
                    () -> globalSearch(hydeDoc, globalTopK), retrievalExecutor);
            hydeIntentFuture = (kbId != null)
                    ? CompletableFuture.supplyAsync(() -> intentDirectedSearch(hydeDoc, kbId, intentTopK), retrievalExecutor)
                    : CompletableFuture.completedFuture(List.of());
        } else {
            hydeGlobalFuture = CompletableFuture.completedFuture(List.of());
            hydeIntentFuture = CompletableFuture.completedFuture(List.of());
        }

        // 等价查询通道（条件触发）
        List<CompletableFuture<List<Document>>> equivFutures = new ArrayList<>();
        if (equivQueries != null && !equivQueries.isEmpty()) {
            for (String eq : equivQueries) {
                equivFutures.add(CompletableFuture.supplyAsync(
                        () -> globalSearch(eq, globalTopK), retrievalExecutor));
            }
        }

        // 合并所有 future
        List<CompletableFuture<List<Document>>> allFutures = new ArrayList<>();
        allFutures.add(globalFuture);
        allFutures.add(intentFuture);
        allFutures.add(fulltextFuture);
        allFutures.add(hydeGlobalFuture);
        allFutures.add(hydeIntentFuture);
        allFutures.addAll(equivFutures);

        String[] channelNames;
        if (hydeDoc != null && !hydeDoc.isBlank() && equivQueries != null && !equivQueries.isEmpty()) {
            channelNames = new String[]{
                    "vector_global", "vector_intent", "fulltext",
                    "hyde_global", "hyde_intent"
            };
            // 动态追加等价查询通道名
            String[] allNames = Arrays.copyOf(channelNames, channelNames.length + equivQueries.size());
            for (int i = 0; i < equivQueries.size(); i++) {
                allNames[channelNames.length + i] = "equiv_" + (i + 1);
            }
            channelNames = allNames;
        } else if (hydeDoc != null && !hydeDoc.isBlank()) {
            channelNames = new String[]{"vector_global", "vector_intent", "fulltext", "hyde_global", "hyde_intent"};
        } else {
            channelNames = new String[]{"vector_global", "vector_intent", "fulltext"};
        }

        try {
            List<Document>[] results = new List[allFutures.size()];
            for (int i = 0; i < allFutures.size(); i++) {
                results[i] = allFutures.get(i).get(timeoutSec, TimeUnit.SECONDS);
            }

            List<ChannelResult> channels = new ArrayList<>();
            for (int i = 0; i < channelNames.length && i < results.length; i++) {
                channels.add(new ChannelResult(channelNames[i], results[i]));
            }
            // 补充等价查询结果
            for (int i = 0; i < equivFutures.size() && (channelNames.length + i) < results.length; i++) {
                // already handled above
            }

            List<Document> merged = rrfFusion(channels, topK, rrfK);

            log.info("[MultiChannelRetriever] 通道数:{} 全局:{} 定向:{} 全文:{} HyDE-G:{} HyDE-I:{} 等价查询通道:{} RRF合并:{}",
                    channels.size(),
                    results[0].size(), results[1].size(), results[2].size(),
                    results.length > 3 ? results[3].size() : 0,
                    results.length > 4 ? results[4].size() : 0,
                    equivFutures.size(), merged.size());
            return merged;
        } catch (Exception e) {
            log.warn("[MultiChannelRetriever] 并行检索异常，降级为单通道: {}", e.getMessage());
            return rrfFusion(List.of(new ChannelResult("vector_global", globalSearch(query, globalTopK))), topK, rrfK);
        }
    }

    /**
     * 多通道检索（不含 HyDE 通道，向后兼容）。
     */
    public List<Document> retrieve(String query, Long kbId, RagConfigDO config) {
        return retrieve(query, kbId, config, null, null);
    }

    /**
     * 向后兼容旧调用。
     */
    public List<Document> retrieve(String query, Long kbId) {
        return retrieve(query, kbId, null);
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

    private List<Document> rrfFusion(List<ChannelResult> channels, int topK, double rrfK) {
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
                scores.merge(id, 1.0 / (rrfK + i + 1), Double::sum);
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
