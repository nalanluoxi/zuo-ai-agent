package com.example.zuoaiagent.trace.service;

/**
 * RAG 链路追踪记录服务
 *
 * <p>在 SmartRagPipeline 的各阶段调用，记录每次执行的流程、耗时和数据。
 *
 * <p>生命周期：
 * <pre>
 *   startRun()
 *     startNode("rewrite")   → finishNode("rewrite", ...)
 *     startNode("classify")  → finishNode("classify", ...)
 *     startNode("retrieve")  → finishNode("retrieve", ...)
 *     startNode("rerank")    → finishNode("rerank", ...)
 *     startNode("prompt")    → finishNode("prompt", ...)
 *   finishRun()
 * </pre>
 */
public interface RagTraceRecordService {

    /**
     * 流水线开始，插入 run 记录（status=RUNNING）。
     *
     * @param traceId        全局链路 ID
     * @param conversationId 会话 ID
     * @param originalPrompt 用户原始问题
     */
    void startRun(String traceId, String conversationId, String originalPrompt);

    /**
     * 设置灰度标签（异步更新已插入的 run 记录）。
     *
     * @param traceId 链路 ID
     * @param grayTag 灰度标签（BASELINE/TAG_A/TAG_B）
     */
    void setGrayTag(String traceId, String grayTag);

    /**
     * 流水线结束，更新 run 记录（status=SUCCESS/ERROR，填写耗时）。
     *
     * @param traceId      链路 ID
     * @param status       SUCCESS 或 ERROR
     * @param errorMessage 错误信息（成功时传 null）
     * @param durationMs   总耗时（毫秒）
     */
    void finishRun(String traceId, String status, String errorMessage, long durationMs);

    /**
     * 节点开始，插入 node 记录（status=RUNNING）。
     *
     * @param traceId   链路 ID
     * @param nodeId    节点唯一 ID（如 "rewrite"）
     * @param nodeName  节点展示名称
     * @param nodeType  节点类型（REWRITE/CLASSIFY/RETRIEVE/RERANK/PROMPT/LLM）
     * @param inputData 节点输入（JSON 字符串，可为 null）
     */
    void startNode(String traceId, String nodeId, String nodeName, String nodeType, String inputData);

    /**
     * 节点结束，更新 node 记录（status=SUCCESS/ERROR，填写耗时和输出）。
     *
     * @param traceId      链路 ID
     * @param nodeId       节点唯一 ID
     * @param status       SUCCESS 或 ERROR
     * @param errorMessage 错误信息（成功时传 null）
     * @param durationMs   节点耗时（毫秒）
     * @param outputData   节点输出（JSON 字符串，可为 null）
     */
    void finishNode(String traceId, String nodeId, String status, String errorMessage,
                    long durationMs, String outputData);

    void finishNode(String traceId, String nodeId, String status, String errorMessage,
                    long durationMs, String outputData, int promptTokens, int completionTokens);
}
