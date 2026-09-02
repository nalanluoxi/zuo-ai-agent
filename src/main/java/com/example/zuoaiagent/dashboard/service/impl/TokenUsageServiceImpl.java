package com.example.zuoaiagent.dashboard.service.impl;

import com.example.zuoaiagent.dashboard.service.TokenUsageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Token 使用量统计服务实现
 */
@Service
public class TokenUsageServiceImpl implements TokenUsageService {

    private static final Logger log = LoggerFactory.getLogger(TokenUsageServiceImpl.class);
    private final JdbcTemplate jdbcTemplate;

    public TokenUsageServiceImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void recordUsage(String userId, String conversationId, String messageId, String model,
                           Integer promptTokens, Integer completionTokens, Integer totalTokens) {
        recordUsage(userId, conversationId, messageId, model, promptTokens, completionTokens, totalTokens, "CONVERSATION");
    }

    @Override
    public void recordUsage(String userId, String conversationId, String messageId, String model,
                           Integer promptTokens, Integer completionTokens, Integer totalTokens, String usageType) {
        try {
            String sql = "INSERT INTO t_token_usage (user_id, conversation_id, message_id, model, " +
                        "input_tokens, output_tokens, total_tokens, usage_type, created_at) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, NOW())";

            Long userIdLong = null;
            if (userId != null && !userId.isBlank()) {
                try {
                    userIdLong = Long.parseLong(userId);
                } catch (NumberFormatException e) {
                    // userId 可能是非数字字符串（如 "system"），忽略
                    log.debug("userId 非数字，跳过: {}", userId);
                }
            }
            jdbcTemplate.update(sql, userIdLong, conversationId, messageId, model,
                              promptTokens, completionTokens, totalTokens, usageType);

            log.info("记录Token使用量: userId={}, conversationId={}, model={}, totalTokens={}, usageType={}",
                     userId, conversationId, model, totalTokens, usageType);
        } catch (Exception e) {
            log.error("记录Token使用量失败: userId={}, conversationId={}, messageId={}",
                     userId, conversationId, messageId, e);
        }
    }
}
