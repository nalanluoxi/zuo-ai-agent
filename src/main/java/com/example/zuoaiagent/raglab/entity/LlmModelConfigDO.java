package com.example.zuoaiagent.raglab.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.util.Date;

/**
 * LLM 模型配置实体
 */
@TableName("t_llm_model_config")
public class LlmModelConfigDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 显示名称 */
    private String modelName;

    /** 提供商: bailian/deepseek/siliconflow/openai/agnes */
    private String provider;

    /** API 地址 */
    private String baseUrl;

    /** API Key（Jasypt 加密存储） */
    private String apiKey;

    /** 平台模型标识 */
    private String modelId;

    /** 最大 Token 数 */
    private Integer maxTokens;

    /** 温度参数 */
    private Double temperature;

    /** 是否激活（对话模块下拉可见） */
    private Integer isActive;

    /** 状态: DRAFT/GRAYING/ACTIVE */
    private String status;

    /** 创建人 ID */
    private Long createUserId;

    private Date createTime;
    private Date updateTime;

    // ===== Getters & Setters =====

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getModelName() { return modelName; }
    public void setModelName(String modelName) { this.modelName = modelName; }

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }

    public String getModelId() { return modelId; }
    public void setModelId(String modelId) { this.modelId = modelId; }

    public Integer getMaxTokens() { return maxTokens; }
    public void setMaxTokens(Integer maxTokens) { this.maxTokens = maxTokens; }

    public Double getTemperature() { return temperature; }
    public void setTemperature(Double temperature) { this.temperature = temperature; }

    public Integer getIsActive() { return isActive; }
    public void setIsActive(Integer isActive) { this.isActive = isActive; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Long getCreateUserId() { return createUserId; }
    public void setCreateUserId(Long createUserId) { this.createUserId = createUserId; }

    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date createTime) { this.createTime = createTime; }

    public Date getUpdateTime() { return updateTime; }
    public void setUpdateTime(Date updateTime) { this.updateTime = updateTime; }
}
