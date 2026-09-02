package com.example.zuoaiagent.knowledge.service;

import com.example.zuoaiagent.knowledge.entity.IngestionLogDO;

public interface IngestionLogService {
    void logUpload(IngestionLogDO log);
    void logParse(IngestionLogDO log);
    void logChunk(IngestionLogDO log);
    void logVectorize(IngestionLogDO log);
    void logComplete(IngestionLogDO log);
    void logFailed(IngestionLogDO log, String errorMessage);
}
