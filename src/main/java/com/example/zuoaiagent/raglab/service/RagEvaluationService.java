package com.example.zuoaiagent.raglab.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.zuoaiagent.intent.model.IntentResult;
import com.example.zuoaiagent.pipeline.RagPipelineContext;
import com.example.zuoaiagent.pipeline.SmartRagPipeline;
import com.example.zuoaiagent.raglab.entity.RagExperimentDO;
import com.example.zuoaiagent.raglab.entity.RagExperimentPlanDO;
import com.example.zuoaiagent.raglab.entity.RagTestQuestionDO;
import com.example.zuoaiagent.raglab.mapper.RagExperimentMapper;
import com.example.zuoaiagent.raglab.mapper.RagExperimentPlanMapper;
import com.example.zuoaiagent.trace.entity.RagTraceNodeDO;
import com.example.zuoaiagent.trace.mapper.RagTraceNodeMapper;
import com.example.zuoaiagent.trace.service.RagTraceRecordService;
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
 * RAG 评估引擎服务（重写版：走 SmartRagPipeline 全链路）
 *
 * <p>实验评估现在完全走用户使用的 SmartRagPipeline，包括：
 * - 查询改写（REWRITE）
 * - HyDE 假设生成（HYDE）
 * - 意图识别（CLASSIFY）
 * - 多通道检索（RETRIEVE）
 * - 重排序（RERANK）
 * - Prompt 组装（PROMPT）
 * - LLM 生成（LLM）
 *
 * <p>每道题的执行都会产生完整的 Trace 记录，可跳转到 TraceDetailPage 查看详情。
 */
@Service
public class RagEvaluationService {

    private static final Logger log = LoggerFactory.getLogger(RagEvaluationService.class);

    private final RagExperimentMapper experimentMapper;
    private final RagExperimentPlanMapper planMapper;
    private final RagTestQuestionService testQuestionService;
    private final SmartRagPipeline smartRagPipeline;
    private final RagTraceNodeMapper traceNodeMapper;
    private final ObjectMapper objectMapper;

    public RagEvaluationService(RagExperimentMapper experimentMapper,
                                 RagExperimentPlanMapper planMapper,
                                 RagTestQuestionService testQuestionService,
                                 SmartRagPipeline smartRagPipeline,
                                 RagTraceNodeMapper traceNodeMapper,
                                 ObjectMapper objectMapper) {
        this.experimentMapper = experimentMapper;
        this.planMapper = planMapper;
        this.testQuestionService = testQuestionService;
        this.smartRagPipeline = smartRagPipeline;
        this.traceNodeMapper = traceNodeMapper;
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
            List<RagTestQuestionDO> questions = testQuestionService.getQuestionsByIds(questionIds);

            int intentCorrect = 0;
            int totalQuestions = questions.size();

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
                    // 使用 SmartRagPipeline 执行全链路
                    String conversationId = "exp-" + experimentId + "-q" + (i + 1);
                    RagPipelineContext ctx = new RagPipelineContext(
                            q.getQuestionText(),
                            conversationId,
                            "实验助手",
                            true,  // enableRewrite
                            true,  // enableRerank
                            false, // enableMemory
                            null   // userId
                    );

                    // 同步执行 Pipeline，传入 experimentId
                    ctx = smartRagPipeline.executeSync(ctx, experimentId);

                    // 记录 traceId 以便前端跳转
                    detail.put("traceId", ctx.getTraceId());
                    detail.put("rewritten", ctx.getRewrittenQuery());

                    // 从 Trace 记录收集各阶段数据
                    Map<String, RagTraceNodeDO> nodes = loadTraceNodes(ctx.getTraceId());

                    // 意图分类结果
                    RagTraceNodeDO classifyNode = nodes.get("classify");
                    if (classifyNode != null) {
                        Map<String, Object> classifyOutput = parseJsonMap(classifyNode.getOutputData());
                        String intentLabel = (String) classifyOutput.get("label");
                        Double confidence = (Double) classifyOutput.get("confidence");
                        detail.put("intentLabel", intentLabel);
                        detail.put("intentConfidence", confidence);

                        // 意图正确性
                        boolean intentOk = checkIntentCorrect(q.getExpectedIntent(), intentLabel);
                        detail.put("intentCorrect", intentOk);
                        if (intentOk) intentCorrect++;
                    }

                    // 检索结果
                    RagTraceNodeDO retrieveNode = nodes.get("retrieve");
                    List<String> retrievedIds = new ArrayList<>();
                    if (retrieveNode != null) {
                        Map<String, Object> retrieveOutput = parseJsonMap(retrieveNode.getOutputData());
                        List<Map<String, Object>> docs = (List<Map<String, Object>>) retrieveOutput.get("docs");
                        if (docs != null) {
                            retrievedIds = docs.stream()
                                    .map(doc -> String.valueOf(doc.get("docId")))
                                    .collect(Collectors.toList());
                        }
                    }
                    detail.put("retrievedDocIds", retrievedIds);

                    // 解析期望文档 ID
                    List<String> expectedDocIds = parseExpectedDocIds(q.getExpectedDocIds());

                    // 计算检索指标
                    double r3 = recallAt(retrievedIds, expectedDocIds, 3);
                    double r5 = recallAt(retrievedIds, expectedDocIds, 5);
                    double r10 = recallAt(retrievedIds, expectedDocIds, 10);
                    recallAt3List.add(r3);
                    recallAt5List.add(r5);
                    recallAt10List.add(r10);
                    detail.put("recallAt3", r3);
                    detail.put("recallAt5", r5);
                    detail.put("recallAt10", r10);

                    double mrr = computeMrr(retrievedIds, expectedDocIds);
                    mrrList.add(mrr);
                    detail.put("mrr", mrr);

                    // Rerank 结果
                    RagTraceNodeDO rerankNode = nodes.get("rerank");
                    List<String> rerankedIds = new ArrayList<>();
                    if (rerankNode != null) {
                        Map<String, Object> rerankOutput = parseJsonMap(rerankNode.getOutputData());
                        List<Map<String, Object>> docs = (List<Map<String, Object>>) rerankOutput.get("docs");
                        if (docs != null) {
                            rerankedIds = docs.stream()
                                    .map(doc -> String.valueOf(doc.get("docId")))
                                    .collect(Collectors.toList());
                        }
                    }
                    detail.put("rerankedDocIds", rerankedIds);

                    double n3 = ndcgAt(rerankedIds, expectedDocIds, 3);
                    double n5 = ndcgAt(rerankedIds, expectedDocIds, 5);
                    ndcgAt3List.add(n3);
                    ndcgAt5List.add(n5);
                    detail.put("ndcgAt3", n3);
                    detail.put("ndcgAt5", n5);

                    // LLM 生成结果
                    detail.put("generatedAnswer", ctx.getGeneratedAnswer());

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

    // ==================== 辅助方法 ====================

    /**
     * 从数据库加载指定 traceId 的所有节点
     */
    private Map<String, RagTraceNodeDO> loadTraceNodes(String traceId) {
        Map<String, RagTraceNodeDO> result = new HashMap<>();
        try {
            List<RagTraceNodeDO> nodes = traceNodeMapper.selectList(
                    new LambdaQueryWrapper<RagTraceNodeDO>()
                            .eq(RagTraceNodeDO::getTraceId, traceId)
            );
            for (RagTraceNodeDO node : nodes) {
                result.put(node.getNodeId(), node);
            }
        } catch (Exception e) {
            log.warn("[RagEvaluation] 加载 Trace 节点失败: traceId={}", traceId);
        }
        return result;
    }

    private Map<String, Object> parseJsonMap(String json) {
        if (json == null || json.isEmpty()) return Map.of();
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (Exception e) {
            return Map.of();
        }
    }

    private boolean checkIntentCorrect(String expectedIntent, String actualLabel) {
        if (expectedIntent == null || expectedIntent.isEmpty()) return true;
        if (actualLabel == null) return false;
        return expectedIntent.equalsIgnoreCase(actualLabel)
                || expectedIntent.contains(actualLabel)
                || actualLabel.contains(expectedIntent);
    }

    private List<String> parseExpectedDocIds(String json) {
        if (json == null || json.isEmpty()) return List.of();
        try {
            return objectMapper.readValue(json, objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
        } catch (Exception e) {
            return Arrays.asList(json.split("[,;]"));
        }
    }

    private double recallAt(List<String> retrievedIds, List<String> expectedIds, int k) {
        if (expectedIds.isEmpty()) return 1.0;
        Set<String> topK = new HashSet<>(retrievedIds.subList(0, Math.min(k, retrievedIds.size())));
        long hit = expectedIds.stream().filter(topK::contains).count();
        return (double) hit / expectedIds.size();
    }

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

    private double ndcgAt(List<String> rankedIds, List<String> expectedIds, int k) {
        if (expectedIds.isEmpty()) return 1.0;
        Set<String> expected = new HashSet<>(expectedIds);
        int limit = Math.min(k, rankedIds.size());

        double dcg = 0;
        for (int i = 0; i < limit; i++) {
            double rel = expected.contains(rankedIds.get(i)) ? 1.0 : 0.0;
            dcg += rel / (Math.log(i + 2) / Math.log(2));
        }

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
