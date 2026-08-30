package com.example.zuoaiagent.trace.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import java.util.Date;

/**
 * RAG 节点级别追踪记录（对应 t_rag_trace_node）
 *
 * <p>每个流水线阶段（改写/分类/检索/重排序/Prompt组装/LLM）各产生一条记录，
 * {@code inputData} / {@code outputData} 以 JSON 字符串存储阶段输入输出。
 *
 * <p>节点类型枚举（{@code nodeType}）：
 * <ul>
 *   <li>REWRITE — 查询改写</li>
 *   <li>CLASSIFY — 意图分类</li>
 *   <li>RETRIEVE — 多通道检索</li>
 *   <li>RERANK — 重排序</li>
 *   <li>PROMPT — Prompt 组装</li>
 *   <li>LLM — 模型调用</li>
 * </ul>
 */
@TableName("t_rag_trace_node")
public class RagTraceNodeDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联 t_rag_trace_run.trace_id */
    private String traceId;

    /** 节点唯一 ID（如 "rewrite"、"classify"、"retrieve"） */
    private String nodeId;

    /** 节点展示名称 */
    private String nodeName;

    /** 节点类型：REWRITE / CLASSIFY / RETRIEVE / RERANK / PROMPT / LLM */
    private String nodeType;

    /** 状态：RUNNING / SUCCESS / ERROR */
    private String status;

    /** 错误信息 */
    private String errorMessage;

    /** 节点开始时间 */
    private Date startTime;

    /** 节点结束时间 */
    private Date endTime;

    /** 节点耗时（毫秒） */
    private Long durationMs;

    /** 节点输入（JSON 字符串） */
    private String inputData;

    /** 节点输出（JSON 字符串，含检索文档内容、分类结果等） */
    private String outputData;

    /** Prompt Token 用量 */
    private Integer promptTokens;

    /** Completion Token 用量 */
    private Integer completionTokens;

    @TableField(fill = FieldFill.INSERT)
    private Date createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;

    @TableLogic
    private Short deleted;

    public RagTraceNodeDO() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTraceId() { return traceId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }

    public String getNodeId() { return nodeId; }
    public void setNodeId(String nodeId) { this.nodeId = nodeId; }

    public String getNodeName() { return nodeName; }
    public void setNodeName(String nodeName) { this.nodeName = nodeName; }

    public String getNodeType() { return nodeType; }
    public void setNodeType(String nodeType) { this.nodeType = nodeType; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public Date getStartTime() { return startTime; }
    public void setStartTime(Date startTime) { this.startTime = startTime; }

    public Date getEndTime() { return endTime; }
    public void setEndTime(Date endTime) { this.endTime = endTime; }

    public Long getDurationMs() { return durationMs; }
    public void setDurationMs(Long durationMs) { this.durationMs = durationMs; }

    public String getInputData() { return inputData; }
    public void setInputData(String inputData) { this.inputData = inputData; }

    public String getOutputData() { return outputData; }
    public void setOutputData(String outputData) { this.outputData = outputData; }

    public Integer getPromptTokens() { return promptTokens; }
    public void setPromptTokens(Integer promptTokens) { this.promptTokens = promptTokens; }

    public Integer getCompletionTokens() { return completionTokens; }
    public void setCompletionTokens(Integer completionTokens) { this.completionTokens = completionTokens; }

    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date createTime) { this.createTime = createTime; }

    public Date getUpdateTime() { return updateTime; }
    public void setUpdateTime(Date updateTime) { this.updateTime = updateTime; }

    public Short getDeleted() { return deleted; }
    public void setDeleted(Short deleted) { this.deleted = deleted; }
}
