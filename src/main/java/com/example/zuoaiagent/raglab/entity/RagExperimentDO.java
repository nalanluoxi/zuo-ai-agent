package com.example.zuoaiagent.raglab.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.util.Date;

/**
 * RAG 实验（对应 t_rag_experiment）
 */
@TableName("t_rag_experiment")
public class RagExperimentDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 实验名称 */
    private String experimentName;

    /** 配置版本 ID */
    private Long configVersionId;

    /** 测试问题 ID 列表 */
    private String testQuestionIds;

    /** 意图准确率 */
    private Double intentAccuracy;

    /** 改写准确率 */
    private Double rewriteAccuracy;

    /** HyDE 相关性 */
    private Double hydeRelevance;

    /** Recall@3 */
    private Double recallAt3;

    /** Recall@5 */
    private Double recallAt5;

    /** Recall@10 */
    private Double recallAt10;

    /** MRR（Mean Reciprocal Rank） */
    private Double mrr;

    /** Rerank NDCG@3 */
    private Double rerankNdcgAt3;

    /** Rerank NDCG@5 */
    private Double rerankNdcgAt5;

    /** 答案忠实度 */
    private Double answerFaithfulness;

    /** 答案完整性 */
    private Double answerCompleteness;

    /** 幻觉率 */
    private Double hallucinationRate;

    /** 详细数据（JSON） */
    private String detailData;

    /** 状态 */
    private String status;

    /** 创建时间 */
    private Date createTime;

    /** 完成时间 */
    private Date finishTime;

    /** 创建用户 ID */
    private Long createUserId;

    /** 运行时长（毫秒） */
    private Long runDurationMs;

    /** 租户 ID */
    private Long tenantId;

    public RagExperimentDO() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getExperimentName() { return experimentName; }
    public void setExperimentName(String experimentName) { this.experimentName = experimentName; }

    public Long getConfigVersionId() { return configVersionId; }
    public void setConfigVersionId(Long configVersionId) { this.configVersionId = configVersionId; }

    public String getTestQuestionIds() { return testQuestionIds; }
    public void setTestQuestionIds(String testQuestionIds) { this.testQuestionIds = testQuestionIds; }

    public Double getIntentAccuracy() { return intentAccuracy; }
    public void setIntentAccuracy(Double intentAccuracy) { this.intentAccuracy = intentAccuracy; }

    public Double getRewriteAccuracy() { return rewriteAccuracy; }
    public void setRewriteAccuracy(Double rewriteAccuracy) { this.rewriteAccuracy = rewriteAccuracy; }

    public Double getHydeRelevance() { return hydeRelevance; }
    public void setHydeRelevance(Double hydeRelevance) { this.hydeRelevance = hydeRelevance; }

    public Double getRecallAt3() { return recallAt3; }
    public void setRecallAt3(Double recallAt3) { this.recallAt3 = recallAt3; }

    public Double getRecallAt5() { return recallAt5; }
    public void setRecallAt5(Double recallAt5) { this.recallAt5 = recallAt5; }

    public Double getRecallAt10() { return recallAt10; }
    public void setRecallAt10(Double recallAt10) { this.recallAt10 = recallAt10; }

    public Double getMrr() { return mrr; }
    public void setMrr(Double mrr) { this.mrr = mrr; }

    public Double getRerankNdcgAt3() { return rerankNdcgAt3; }
    public void setRerankNdcgAt3(Double rerankNdcgAt3) { this.rerankNdcgAt3 = rerankNdcgAt3; }

    public Double getRerankNdcgAt5() { return rerankNdcgAt5; }
    public void setRerankNdcgAt5(Double rerankNdcgAt5) { this.rerankNdcgAt5 = rerankNdcgAt5; }

    public Double getAnswerFaithfulness() { return answerFaithfulness; }
    public void setAnswerFaithfulness(Double answerFaithfulness) { this.answerFaithfulness = answerFaithfulness; }

    public Double getAnswerCompleteness() { return answerCompleteness; }
    public void setAnswerCompleteness(Double answerCompleteness) { this.answerCompleteness = answerCompleteness; }

    public Double getHallucinationRate() { return hallucinationRate; }
    public void setHallucinationRate(Double hallucinationRate) { this.hallucinationRate = hallucinationRate; }

    public String getDetailData() { return detailData; }
    public void setDetailData(String detailData) { this.detailData = detailData; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date createTime) { this.createTime = createTime; }

    public Date getFinishTime() { return finishTime; }
    public void setFinishTime(Date finishTime) { this.finishTime = finishTime; }

    public Long getCreateUserId() { return createUserId; }
    public void setCreateUserId(Long createUserId) { this.createUserId = createUserId; }

    public Long getRunDurationMs() { return runDurationMs; }
    public void setRunDurationMs(Long runDurationMs) { this.runDurationMs = runDurationMs; }

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
}
