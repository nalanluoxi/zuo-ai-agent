package com.example.zuoaiagent.dashboard.service.impl;

import com.example.zuoaiagent.dashboard.service.RetrievalLogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 检索日志统计服务实现
 */
@Service
public class RetrievalLogServiceImpl implements RetrievalLogService {

    private static final Logger log = LoggerFactory.getLogger(RetrievalLogServiceImpl.class);
    private final JdbcTemplate jdbcTemplate;

    public RetrievalLogServiceImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void recordRetrieval(String userId, String conversationId, String messageId,
                               String knowledgeBaseId, String queryText, Integer resultCount,
                               Double relevanceScore, Integer latencyMs) {
        try {
            String sql = "INSERT INTO t_retrieval_log (user_id, conversation_id, message_id, " +
                        "knowledge_base_id, query_text, result_count, relevance_score, latency_ms, created_at) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

            Long userIdLong = null;
            if (userId != null && !userId.isBlank()) {
                userIdLong = Long.parseLong(userId);
            }
            jdbcTemplate.update(sql, userIdLong, conversationId, messageId,
                              knowledgeBaseId, queryText, resultCount, relevanceScore, latencyMs,
                              LocalDateTime.now());

            log.info("记录检索日志: userId={}, kbId={}, resultCount={}, latencyMs={}",
                     userId, knowledgeBaseId, resultCount, latencyMs);
        } catch (Exception e) {
            log.error("记录检索日志失败: userId={}, kbId={}", userId, knowledgeBaseId, e);
        }
    }
}
