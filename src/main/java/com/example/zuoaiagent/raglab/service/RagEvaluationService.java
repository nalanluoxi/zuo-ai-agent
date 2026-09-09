package com.example.zuoaiagent.raglab.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.zuoaiagent.intent.model.IntentResult;
import com.example.zuoaiagent.intent.service.IntentClassifier;
import com.example.zuoaiagent.rag.DocumentReranker;
import com.example.zuoaiagent.rag.HyDEQueryRewriter;
import com.example.zuoaiagent.rag.MultiChannelRetriever;
import com.example.zuoaiagent.rag.QueryRewriter;
import com.example.zuoaiagent.raglab.entity.RagConfigDO;
import com.example.zuoaiagent.raglab.entity.RagExperimentDO;
import com.example.zuoaiagent.raglab.entity.RagExperimentPlanDO;
import com.example.zuoaiagent.raglab.entity.RagTestQuestionDO;
import com.example.zuoaiagent.raglab.mapper.RagExperimentMapper;
import com.example.zuoaiagent.raglab.mapper.RagExperimentPlanMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * RAG 评估引擎服务
 *
 * <p>对指定测试题库执行 RAG 流水线各阶段，计算多维度指标。
 */
@Service
public class RagEvaluationService {

    private static final Logger log = LoggerFactory.getLogger(RagEvaluationService.class);

    private final RagExperimentMapper experimentMapper;
    private final RagExperimentPlanMapper planMapper;
    private final RagTestQuestionService testQuestionService;
    private final RagConfigLoader configLoader;
    private final QueryRewriter queryRewriter;
    private final IntentClassifier intentClassifier;
    private final MultiChannelRetriever multiChannelRetriever;
    private final DocumentReranker documentReranker;
    private final HyDEQueryRewriter hydeQueryRewriter;
    private final ObjectMapper objectMapper;

    public RagEvaluationService(RagExperimentMapper experimentMapper,
                                 RagExperimentPlanMapper planMapper,
                                 RagTestQuestionService testQuestionService,
                                 RagConfigLoader configLoader,
                                 QueryRewriter queryRewriter,
                                 IntentClassifier intentClassifier,
                                 MultiChannelRetriever multiChannelRetriever,
                                 DocumentReranker documentReranker,
                                 HyDEQueryRewriter hydeQueryRewriter,
                                 ObjectMapper objectMapper) {
        this.experimentMapper = experimentMapper;
        this.planMapper = planMapper;
        this.testQuestionService = testQuestionService;
        this.configLoader = configLoader;
        this.queryRewriter = queryRewriter;
        this.intentClassifier = intentClassifier;
        this.multiChannelRetriever = multiChannelRetriever;
        this.documentReranker = documentReranker;
        this.hydeQueryRewriter = hydeQueryRewriter;
        this.objectMapper = objectMapper;
    }

    // ==================== 实验管理 ====================

    public List<RagExperimentDO> listExperiments() {
        return experimentMapper.selectList(
                new LambdaQueryWrapper<RagExperimentDO>().orderByDesc(RagExperimentDO::getCreateTime));
    }

    public RagExperimentDO getExperiment(Long id) {
        return experimentMapper.selectById(id);
    }

    /**
     * 启动评估实验（异步执行）。
     */
    public RagExperimentDO startExperiment(String name, List<Long> questionIds) {
        RagExperimentDO exp = createExperimentRecord(name, questionIds);
        runExperimentAsync(exp.getId(), questionIds);
        return exp;
    }

    /**
     * 仅创建实验记录（不启动执行）。
     */
    public RagExperimentDO createExperimentRecord(String name, List<Long> questionIds) {
        RagExperimentDO exp = new RagExperimentDO();
        exp.setExperimentName(name);
        exp.setTestQuestionIds(toJson(questionIds));
        exp.setStatus("RUNNING");
        exp.setCreateTime(new Date());
        experimentMapper.insert(exp);
        return exp;
    }

    @Async("ingestionExecutor")
    public void runExperimentAsync(Long experimentId, List<Long> questionIds) {
        long startMs = System.currentTimeMillis();
        try {
            RagConfigDO config = configLoader.getActiveConfig();
            List<RagTestQuestionDO> questions = testQuestionService.getQuestionsByIds(questionIds);

            int intentCorrect = 0;
            int totalQuestions = questions.size();

            // 收集检索指标
            List<Double> recallAt3List = new ArrayList<>();
            List<Double> recallAt5List = new ArrayList<>();
            List<Double> recallAt10List = new ArrayList<>();
            List<Double> mrrList = new ArrayList<>();
            List<Double> ndcgAt3List = new ArrayList<>();
            List<Double> ndcgAt5List = new ArrayList<>();

            List<Map<String, Object>> details = new ArrayList<>();

            for (int i = 0; i < questions.size(); i++) {
                RagTestQuestionDO q = questions.get(i);
                Map<String, Object> detail = new LinkedHashMap<>();
                detail.put("questionId", q.getId());
                detail.put("question", q.getQuestionText());

                try {
                    // 1. 查询改写
                    String rewritten = queryRewriter.rewrite(q.getQuestionText());
                    detail.put("rewritten", rewritten);

                    // 2. 意图分类
                    double highConf = config != null && config.getIntentConfidenceThreshold() != null
                            ? config.getIntentConfidenceThreshold() : 0.85;
                    IntentResult intent = intentClassifier.classify(rewritten, highConf);
                    detail.put("intentLabel", intent.getLabel());
                    detail.put("intentConfidence", intent.getConfidence());

                    // 意图正确性
                    boolean intentOk = checkIntentCorrect(q.getExpectedIntent(), intent);
                    detail.put("intentCorrect", intentOk);
                    if (intentOk) intentCorrect++;

                    // 3. 检索
                    Long kbId = intent.getKbId();
                    List<Document> retrieved = multiChannelRetriever.retrieve(rewritten, kbId, config, null, null);
                    List<String> retrievedIds = retrieved.stream()
                            .map(Document::getId)
                            .collect(Collectors.toList());
                    detail.put("retrievedDocIds", retrievedIds);

                    // 解析期望文档 ID
                    List<String> expectedDocIds = parseExpectedDocIds(q.getExpectedDocIds());

                    // Recall@K
                    double r3 = recallAt(retrievedIds, expectedDocIds, 3);
                    double r5 = recallAt(retrievedIds, expectedDocIds, 5);
                    double r10 = recallAt(retrievedIds, expectedDocIds, 10);
                    recallAt3List.add(r3);
                    recallAt5List.add(r5);
                    recallAt10List.add(r10);
                    detail.put("recallAt3", r3);
                    detail.put("recallAt5", r5);
                    detail.put("recallAt10", r10);

                    // MRR
                    double mrr = computeMrr(retrievedIds, expectedDocIds);
                    mrrList.add(mrr);
                    detail.put("mrr", mrr);

                    // 4. Rerank
                    int rerankTopK = config != null && config.getRerankTopK() != null ? config.getRerankTopK() : 3;
                    double rerankThreshold = config != null && config.getRerankConfidenceThreshold() != null
                            ? config.getRerankConfidenceThreshold() : 0.5;
                    int docTruncate = config != null && config.getRerankDocTruncate() != null
                            ? config.getRerankDocTruncate() : 800;
                    List<Document> reranked = documentReranker.rerank(rewritten, retrieved, rerankTopK, rerankThreshold, docTruncate);
                    List<String> rerankedIds = reranked.stream()
                            .map(Document::getId)
                            .collect(Collectors.toList());
                    detail.put("rerankedDocIds", rerankedIds);

                    // NDCG@K
                    double n3 = ndcgAt(rerankedIds, expectedDocIds, 3);
                    double n5 = ndcgAt(rerankedIds, expectedDocIds, 5);
                    ndcgAt3List.add(n3);
                    ndcgAt5List.add(n5);
                    detail.put("ndcgAt3", n3);
                    detail.put("ndcgAt5", n5);

                    detail.put("status", "SUCCESS");
                } catch (Exception e) {
                    log.warn("[RagEvaluation] 第{}题执行失败: {}", i + 1, e.getMessage());
                    detail.put("status", "ERROR");
                    detail.put("error", e.getMessage());
                }

                details.add(detail);
            }

            // 汇总指标
            long durationMs = System.currentTimeMillis() - startMs;

            RagExperimentDO update = new RagExperimentDO();
            update.setId(experimentId);
            update.setStatus("COMPLETED");
            update.setFinishTime(new Date());
            update.setRunDurationMs(durationMs);
            update.setIntentAccuracy(totalQuestions > 0 ? (double) intentCorrect / totalQuestions : 0);
            update.setRecallAt3(avg(recallAt3List));
            update.setRecallAt5(avg(recallAt5List));
            update.setRecallAt10(avg(recallAt10List));
            update.setMrr(avg(mrrList));
            update.setRerankNdcgAt3(avg(ndcgAt3List));
            update.setRerankNdcgAt5(avg(ndcgAt5List));
            update.setDetailData(toJson(details));
            experimentMapper.updateById(update);

            // 回写实验计划状态
            updatePlanStatus(experimentId, "COMPLETED");

            log.info("[RagEvaluation] 实验 #{} 完成, 耗时 {}ms, 意图准确率={}", experimentId, durationMs, update.getIntentAccuracy());
        } catch (Exception e) {
            log.error("[RagEvaluation] 实验 #{} 执行失败", experimentId, e);
            RagExperimentDO update = new RagExperimentDO();
            update.setId(experimentId);
            update.setStatus("FAILED");
            update.setFinishTime(new Date());
            update.setRunDurationMs(System.currentTimeMillis() - startMs);
            experimentMapper.updateById(update);

            // 回写实验计划状态
            updatePlanStatus(experimentId, "FAILED");
        }
    }

    // ==================== 指标计算 ====================

    private boolean checkIntentCorrect(String expectedIntent, IntentResult actual) {
        if (expectedIntent == null || expectedIntent.isEmpty()) return true;
        if (actual == null) return false;
        // 支持精确匹配或包含匹配
        return expectedIntent.equalsIgnoreCase(actual.getLabel())
                || expectedIntent.contains(actual.getLabel())
                || actual.getLabel().contains(expectedIntent);
    }

    private List<String> parseExpectedDocIds(String json) {
        if (json == null || json.isEmpty()) return List.of();
        try {
            return objectMapper.readValue(json, objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
        } catch (Exception e) {
            // 尝试按逗号分隔解析
            return Arrays.asList(json.split("[,;]"));
        }
    }

    /**
     * Recall@K: 期望文档中在前K个被召回的比例
     */
    private double recallAt(List<String> retrievedIds, List<String> expectedIds, int k) {
        if (expectedIds.isEmpty()) return 1.0;
        Set<String> topK = new HashSet<>(retrievedIds.subList(0, Math.min(k, retrievedIds.size())));
        long hit = expectedIds.stream().filter(topK::contains).count();
        return (double) hit / expectedIds.size();
    }

    /**
     * MRR: 1 / 第一个命中位置的排名
     */
    private double computeMrr(List<String> retrievedIds, List<String> expectedIds) {
        if (expectedIds.isEmpty()) return 1.0;
        Set<String> expected = new HashSet<>(expectedIds);
        for (int i = 0; i < retrievedIds.size(); i++) {
            if (expected.contains(retrievedIds.get(i))) {
                return 1.0 / (i + 1);
            }
        }
        return 0.0;
    }

    /**
     * NDCG@K: 归一化折损累计增益
     */
    private double ndcgAt(List<String> rankedIds, List<String> expectedIds, int k) {
        if (expectedIds.isEmpty()) return 1.0;
        Set<String> expected = new HashSet<>(expectedIds);
        int limit = Math.min(k, rankedIds.size());

        // DCG
        double dcg = 0;
        for (int i = 0; i < limit; i++) {
            double rel = expected.contains(rankedIds.get(i)) ? 1.0 : 0.0;
            dcg += rel / (Math.log(i + 2) / Math.log(2)); // log2(i+2)
        }

        // IDCG (理想排序：所有命中在前)
        long totalHits = Math.min(expected.size(), limit);
        double idcg = 0;
        for (int i = 0; i < totalHits; i++) {
            idcg += 1.0 / (Math.log(i + 2) / Math.log(2));
        }

        return idcg > 0 ? dcg / idcg : 0.0;
    }

    private double avg(List<Double> values) {
        if (values.isEmpty()) return 0.0;
        return values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }

    /**
     * 根据实验 ID 查找关联的实验计划并更新状态
     */
    private void updatePlanStatus(Long experimentId, String status) {
        try {
            List<RagExperimentPlanDO> plans = planMapper.selectList(
                    new LambdaQueryWrapper<RagExperimentPlanDO>()
                            .eq(RagExperimentPlanDO::getExperimentId, experimentId)
            );
            for (RagExperimentPlanDO plan : plans) {
                plan.setStatus(status);
                plan.setFinishTime(new Date());
                plan.setUpdateTime(new Date());
                planMapper.updateById(plan);
                log.info("[RagEvaluation] 已更新实验计划状态: planId={}, status={}", plan.getId(), status);
            }
        } catch (Exception e) {
            log.warn("[RagEvaluation] 更新实验计划状态失败: experimentId={}, status={}, error={}", experimentId, status, e.getMessage());
        }
    }
}
