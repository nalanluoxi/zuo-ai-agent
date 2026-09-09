package com.example.zuoaiagent.trace.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.zuoaiagent.trace.entity.RagTraceNodeDO;
import com.example.zuoaiagent.trace.entity.RagTraceRunDO;
import com.example.zuoaiagent.trace.mapper.RagTraceNodeMapper;
import com.example.zuoaiagent.trace.mapper.RagTraceRunMapper;
import com.example.zuoaiagent.trace.service.RagTraceRecordService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Date;

/**
 * RAG 链路追踪记录服务实现
 *
 * <p>所有写库操作均同步执行，保证数据一致性。
 * 写库失败只打 warn 日志，不影响主业务。
 */
@Service
public class RagTraceRecordServiceImpl implements RagTraceRecordService {

    private static final Logger log = LoggerFactory.getLogger(RagTraceRecordServiceImpl.class);

    private final RagTraceRunMapper runMapper;
    private final RagTraceNodeMapper nodeMapper;

    public RagTraceRecordServiceImpl(RagTraceRunMapper runMapper, RagTraceNodeMapper nodeMapper) {
        this.runMapper = runMapper;
        this.nodeMapper = nodeMapper;
    }

    @Override
    public void startRun(String traceId, String conversationId, String originalPrompt) {
        startRun(traceId, conversationId, originalPrompt, null);
    }

    @Override
    public void startRun(String traceId, String conversationId, String originalPrompt, Long experimentId) {
        try {
            RagTraceRunDO run = new RagTraceRunDO();
            run.setTraceId(traceId);
            run.setConversationId(conversationId);
            run.setOriginalPrompt(originalPrompt);
            run.setStatus("RUNNING");
            run.setExperimentId(experimentId);
            run.setStartTime(new Date());
            runMapper.insert(run);
        } catch (Exception e) {
            log.warn("[RagTrace] startRun 写库失败 traceId={}: {}", traceId, e.getMessage());
        }
    }

    @Override
    public void setGrayTag(String traceId, String grayTag) {
        try {
            RagTraceRunDO update = new RagTraceRunDO();
            update.setGrayTag(grayTag);
            runMapper.update(update, new LambdaUpdateWrapper<RagTraceRunDO>()
                    .eq(RagTraceRunDO::getTraceId, traceId));
        } catch (Exception e) {
            log.warn("[RagTrace] setGrayTag 写库失败 traceId={}: {}", traceId, e.getMessage());
        }
    }

    @Override
    public void finishRun(String traceId, String status, String errorMessage, long durationMs) {
        try {
            RagTraceRunDO update = new RagTraceRunDO();
            update.setStatus(status);
            update.setErrorMessage(errorMessage);
            update.setEndTime(new Date());
            update.setDurationMs(durationMs);
            runMapper.update(update, new LambdaUpdateWrapper<RagTraceRunDO>()
                    .eq(RagTraceRunDO::getTraceId, traceId));
        } catch (Exception e) {
            log.warn("[RagTrace] finishRun 写库失败 traceId={}: {}", traceId, e.getMessage());
        }
    }

    @Override
    public void startNode(String traceId, String nodeId, String nodeName, String nodeType, String inputData) {
        try {
            RagTraceNodeDO node = new RagTraceNodeDO();
            node.setTraceId(traceId);
            node.setNodeId(nodeId);
            node.setNodeName(nodeName);
            node.setNodeType(nodeType);
            node.setStatus("RUNNING");
            node.setStartTime(new Date());
            node.setInputData(inputData);
            nodeMapper.insert(node);
        } catch (Exception e) {
            log.warn("[RagTrace] startNode 写库失败 traceId={} nodeId={}: {}", traceId, nodeId, e.getMessage());
        }
    }

    @Override
    public void finishNode(String traceId, String nodeId, String status, String errorMessage,
                           long durationMs, String outputData) {
        finishNode(traceId, nodeId, status, errorMessage, durationMs, outputData, 0, 0);
    }

    @Override
    public void finishNode(String traceId, String nodeId, String status, String errorMessage,
                           long durationMs, String outputData, int promptTokens, int completionTokens) {
        try {
            RagTraceNodeDO update = new RagTraceNodeDO();
            update.setStatus(status);
            update.setErrorMessage(errorMessage);
            update.setEndTime(new Date());
            update.setDurationMs(durationMs);
            update.setOutputData(outputData);
            update.setPromptTokens(promptTokens);
            update.setCompletionTokens(completionTokens);
            nodeMapper.update(update, new LambdaUpdateWrapper<RagTraceNodeDO>()
                    .eq(RagTraceNodeDO::getTraceId, traceId)
                    .eq(RagTraceNodeDO::getNodeId, nodeId));
        } catch (Exception e) {
            log.warn("[RagTrace] finishNode 写库失败 traceId={} nodeId={}: {}", traceId, nodeId, e.getMessage());
        }
    }
}
