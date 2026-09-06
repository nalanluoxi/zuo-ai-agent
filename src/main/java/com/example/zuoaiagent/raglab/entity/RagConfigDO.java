package com.example.zuoaiagent.raglab.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.util.Date;

/**
 * RAG 配置（对应 t_rag_config）
 */
@TableName("t_rag_config")
public class RagConfigDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 配置名称 */
    private String configName;

    /** 意图识别置信度阈值 */
    private Double intentConfidenceThreshold;

    /** 全局检索 TopK */
    private Integer retrieveGlobalTopK;

    /** 意图检索 TopK */
    private Integer retrieveIntentTopK;

    /** 全文检索 TopK */
    private Integer retrieveFulltextTopK;

    /** 检索超时时间（秒） */
    private Integer retrieveTimeoutSec;

    /** RRF 融合参数 k */
    private Double rrfK;

    /** Rerank TopK */
    private Integer rerankTopK;

    /** Rerank 置信度阈值 */
    private Double rerankConfidenceThreshold;

    /** Rerank 文档截断长度 */
    private Integer rerankDocTruncate;

    /** HyDE 是否启用（0/1） */
    private Short hydeEnabled;

    /** HyDE 实验模式（0/1） */
    private Short hydeExperimentMode;

    /** HyDE 实验比例 */
    private Double hydeExperimentRatio;

    /** HyDE 等价查询数量 */
    private Integer hydeEquivQueryCount;

    /** Token 预算 */
    private Integer tokenBudget;

    /** Token 估算系数 */
    private Double tokenEstimateCoefficient;

    /** 灰度是否启用（0/1） */
    private Short grayEnabled;

    /** 灰度比例 */
    private Double grayRatio;

    /** 灰度模式 */
    private String grayMode;

    /** 灰度用户 ID 列表 */
    private String grayUserIds;

    /** 是否激活（0/1） */
    private Short isActive;

    /** 租户 ID */
    private Long tenantId;

    /** 创建时间 */
    private Date createTime;

    /** 更新时间 */
    private Date updateTime;

    /** 创建用户 ID */
    private Long createUserId;

    /** 更新用户 ID */
    private Long updateUserId;

    public RagConfigDO() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getConfigName() { return configName; }
    public void setConfigName(String configName) { this.configName = configName; }

    public Double getIntentConfidenceThreshold() { return intentConfidenceThreshold; }
    public void setIntentConfidenceThreshold(Double intentConfidenceThreshold) { this.intentConfidenceThreshold = intentConfidenceThreshold; }

    public Integer getRetrieveGlobalTopK() { return retrieveGlobalTopK; }
    public void setRetrieveGlobalTopK(Integer retrieveGlobalTopK) { this.retrieveGlobalTopK = retrieveGlobalTopK; }

    public Integer getRetrieveIntentTopK() { return retrieveIntentTopK; }
    public void setRetrieveIntentTopK(Integer retrieveIntentTopK) { this.retrieveIntentTopK = retrieveIntentTopK; }

    public Integer getRetrieveFulltextTopK() { return retrieveFulltextTopK; }
    public void setRetrieveFulltextTopK(Integer retrieveFulltextTopK) { this.retrieveFulltextTopK = retrieveFulltextTopK; }

    public Integer getRetrieveTimeoutSec() { return retrieveTimeoutSec; }
    public void setRetrieveTimeoutSec(Integer retrieveTimeoutSec) { this.retrieveTimeoutSec = retrieveTimeoutSec; }

    public Double getRrfK() { return rrfK; }
    public void setRrfK(Double rrfK) { this.rrfK = rrfK; }

    public Integer getRerankTopK() { return rerankTopK; }
    public void setRerankTopK(Integer rerankTopK) { this.rerankTopK = rerankTopK; }

    public Double getRerankConfidenceThreshold() { return rerankConfidenceThreshold; }
    public void setRerankConfidenceThreshold(Double rerankConfidenceThreshold) { this.rerankConfidenceThreshold = rerankConfidenceThreshold; }

    public Integer getRerankDocTruncate() { return rerankDocTruncate; }
    public void setRerankDocTruncate(Integer rerankDocTruncate) { this.rerankDocTruncate = rerankDocTruncate; }

    public Short getHydeEnabled() { return hydeEnabled; }
    public void setHydeEnabled(Short hydeEnabled) { this.hydeEnabled = hydeEnabled; }

    public Short getHydeExperimentMode() { return hydeExperimentMode; }
    public void setHydeExperimentMode(Short hydeExperimentMode) { this.hydeExperimentMode = hydeExperimentMode; }

    public Double getHydeExperimentRatio() { return hydeExperimentRatio; }
    public void setHydeExperimentRatio(Double hydeExperimentRatio) { this.hydeExperimentRatio = hydeExperimentRatio; }

    public Integer getHydeEquivQueryCount() { return hydeEquivQueryCount; }
    public void setHydeEquivQueryCount(Integer hydeEquivQueryCount) { this.hydeEquivQueryCount = hydeEquivQueryCount; }

    public Integer getTokenBudget() { return tokenBudget; }
    public void setTokenBudget(Integer tokenBudget) { this.tokenBudget = tokenBudget; }

    public Double getTokenEstimateCoefficient() { return tokenEstimateCoefficient; }
    public void setTokenEstimateCoefficient(Double tokenEstimateCoefficient) { this.tokenEstimateCoefficient = tokenEstimateCoefficient; }

    public Short getGrayEnabled() { return grayEnabled; }
    public void setGrayEnabled(Short grayEnabled) { this.grayEnabled = grayEnabled; }

    public Double getGrayRatio() { return grayRatio; }
    public void setGrayRatio(Double grayRatio) { this.grayRatio = grayRatio; }

    public String getGrayMode() { return grayMode; }
    public void setGrayMode(String grayMode) { this.grayMode = grayMode; }

    public String getGrayUserIds() { return grayUserIds; }
    public void setGrayUserIds(String grayUserIds) { this.grayUserIds = grayUserIds; }

    public Short getIsActive() { return isActive; }
    public void setIsActive(Short isActive) { this.isActive = isActive; }

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }

    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date createTime) { this.createTime = createTime; }

    public Date getUpdateTime() { return updateTime; }
    public void setUpdateTime(Date updateTime) { this.updateTime = updateTime; }

    public Long getCreateUserId() { return createUserId; }
    public void setCreateUserId(Long createUserId) { this.createUserId = createUserId; }

    public Long getUpdateUserId() { return updateUserId; }
    public void setUpdateUserId(Long updateUserId) { this.updateUserId = updateUserId; }
}
