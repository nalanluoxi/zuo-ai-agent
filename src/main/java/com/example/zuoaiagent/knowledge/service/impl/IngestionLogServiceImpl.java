package com.example.zuoaiagent.knowledge.service.impl;

import com.example.zuoaiagent.knowledge.entity.IngestionLogDO;
import com.example.zuoaiagent.knowledge.mapper.IngestionLogMapper;
import com.example.zuoaiagent.knowledge.service.IngestionLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class IngestionLogServiceImpl implements IngestionLogService {

    private final IngestionLogMapper ingestionLogMapper;

    @Override
    public void logUpload(IngestionLogDO log) {
        log.setId(null);
        log.setStage("upload");
        log.setStatus("success");
        ingestionLogMapper.insert(log);
    }

    @Override
    public void logParse(IngestionLogDO log) {
        log.setId(null);
        log.setStage("parse");
        log.setStatus("success");
        ingestionLogMapper.insert(log);
    }

    @Override
    public void logChunk(IngestionLogDO log) {
        log.setId(null);
        log.setStage("chunk");
        log.setStatus("success");
        ingestionLogMapper.insert(log);
    }

    @Override
    public void logVectorize(IngestionLogDO log) {
        log.setId(null);
        log.setStage("vectorize");
        log.setStatus("success");
        ingestionLogMapper.insert(log);
    }

    @Override
    public void logComplete(IngestionLogDO log) {
        log.setId(null);
        log.setStage("complete");
        log.setStatus("success");
        ingestionLogMapper.insert(log);
    }

    @Override
    public void logFailed(IngestionLogDO log, String errorMessage) {
        log.setId(null);
        log.setStage(log.getStage() != null ? log.getStage() : "failed");
        log.setStatus("failed");
        log.setErrorMessage(errorMessage);
        ingestionLogMapper.insert(log);
    }
}
