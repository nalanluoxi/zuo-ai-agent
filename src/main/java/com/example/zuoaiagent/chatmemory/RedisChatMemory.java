package com.example.zuoaiagent.chatmemory;

import cn.hutool.core.lang.Snowflake;
import cn.hutool.core.util.IdUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class RedisChatMemory implements ChatMemory {

    private static final Logger log = LoggerFactory.getLogger(RedisChatMemory.class);
    private static final String KEY_PREFIX = "chatmemory:";
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final Snowflake snowflake = IdUtil.getSnowflake(1, 1);

    private final StringRedisTemplate redisTemplate;
    private final RabbitTemplate rabbitTemplate;
    private final JdbcTemplate jdbcTemplate;
    private final int summaryStartTurns;
    private final int compressionBatchSize;
    private final int historyKeep;
    private final int sessionTtlMinutes;

    @Value("${zuo.rabbitmq.queues.chat-memory-persistence:chat.memory.persistence.queue}")
    private String persistenceQueue;

    @Value("${zuo.rabbitmq.queues.chat-memory-compression:chat.memory.compression.queue}")
    private String compressionQueue;

    public RedisChatMemory(StringRedisTemplate redisTemplate,
                           RabbitTemplate rabbitTemplate,
                           JdbcTemplate jdbcTemplate,
                           int summaryStartTurns,
                           int compressionBatchSize,
                           int historyKeep,
                           int sessionTtlMinutes) {
        this.redisTemplate = redisTemplate;
        this.rabbitTemplate = rabbitTemplate;
        this.jdbcTemplate = jdbcTemplate;
        this.summaryStartTurns = summaryStartTurns;
        this.compressionBatchSize = compressionBatchSize;
        this.historyKeep = historyKeep;
        this.sessionTtlMinutes = sessionTtlMinutes;
    }

    /**
     * Redis key = chatmemory:{conversationId}
     * conversationId 是 UUID，全局唯一，不需要加 userId 维度。
     * 用户隔离由 conversation 表（MySQL）保证。
     */
    private String buildKey(String conversationId) {
        return KEY_PREFIX + conversationId;
    }

    @Override
    public void add(String conversationId, Message message) {
        add(conversationId, List.of(message));
    }

    @Override
    public void add(String conversationId, List<Message> messages) {
        String key = buildKey(conversationId);
        log.info("[RedisChatMemory] add() conversationId={}, 添加 {} 条消息", conversationId, messages.size());

        // 统一用 rightPush：timeline 顺序，index 0 = 最旧, index -1 = 最新
        List<String> jsonList = new ArrayList<>();
        for (Message message : messages) {
            String msgJson = serializeMessage(message);
            jsonList.add(msgJson);
            log.info("[RedisChatMemory] 添加消息: role={}, content={}",
                message.getMessageType().getValue(),
                message.getText().length() > 50 ? message.getText().substring(0, 50) + "..." : message.getText());
        }
        redisTemplate.opsForList().rightPushAll(key, jsonList);
        redisTemplate.expire(key, sessionTtlMinutes, TimeUnit.MINUTES);

        Long queueLength = redisTemplate.opsForList().size(key);
        log.info("[RedisChatMemory] conversationId={}, Redis 列表长度: {}", conversationId, queueLength);
        if (queueLength != null && queueLength > summaryStartTurns * 2L) {
            triggerCompression(conversationId, key);
        }

        for (Message message : messages) {
            sendPersistenceMessage(conversationId, message);
        }
    }

    /**
     * 触发压缩：读取队头（最旧的）消息，提取 msgId 快照后发给 RabbitMQ。
     * 因为 rightPush timeline 顺序：index 0 = 最旧。
     */
    private void triggerCompression(String conversationId, String key) {
        List<String> headMessages = redisTemplate.opsForList().range(key, 0, compressionBatchSize - 1);
        if (headMessages == null || headMessages.isEmpty()) {
            return;
        }
        List<String> snapshotMsgIds = new ArrayList<>();
        for (String msgJson : headMessages) {
            try {
                Map<String, Object> msg = objectMapper.readValue(msgJson, Map.class);
                snapshotMsgIds.add(msg.get("msgId").toString());
            } catch (Exception e) {
                log.warn("解析消息失败", e);
            }
        }

        Map<String, Object> compressionMsg = new HashMap<>();
        compressionMsg.put("conversationId", conversationId);
        compressionMsg.put("snapshotMsgIds", snapshotMsgIds);
        rabbitTemplate.convertAndSend(compressionQueue, compressionMsg);
        log.debug("[RedisChatMemory] 压缩消息已发送，conversationId={}, msgCount={}", conversationId, snapshotMsgIds.size());
    }

    private void sendPersistenceMessage(String conversationId, Message message) {
        try {
            Map<String, Object> msg = new HashMap<>();
            msg.put("msgId", objectMapper.readTree(serializeMessage(message)).get("msgId").asText());
            msg.put("conversationId", conversationId);
            msg.put("role", message.getMessageType().getValue());
            msg.put("content", message.getText());
            rabbitTemplate.convertAndSend(persistenceQueue, msg);
        } catch (Exception e) {
            log.warn("发送持久化消息失败", e);
        }
    }

    @Override
    public List<Message> get(String conversationId) {
        return get(conversationId, historyKeep);
    }

    public List<Message> get(String conversationId, int lastN) {
        String key = buildKey(conversationId);
        // timeline 顺序：index 0 = 最旧, index -1 = 最新
        // 取最新 N 条 = 取队尾
        List<String> messages = redisTemplate.opsForList().range(key, -lastN, -1);

        // Redis 为空时，从 MySQL 加载历史消息（lazy warmup）
        if (messages == null || messages.isEmpty()) {
            messages = warmupFromMysql(conversationId, lastN);
            if (!messages.isEmpty()) {
                redisTemplate.opsForList().rightPushAll(key, messages);
                redisTemplate.expire(key, sessionTtlMinutes, TimeUnit.MINUTES);
                log.info("[RedisChatMemory] MySQL warmup, conversationId={}, loaded {} messages", conversationId, messages.size());
                // warmup 后重新按最新 N 条读取（可能 warmup 返回的超过 lastN 或不足）
                messages = redisTemplate.opsForList().range(key, -lastN, -1);
            }
        }

        if (messages == null || messages.isEmpty()) {
            return List.of();
        }
        // timeline 顺序已是 LLM 期望顺序：最旧 → 最新，无需 reverse
        List<Message> result = new ArrayList<>();
        for (String msgJson : messages) {
            try {
                result.add(deserializeMessage(msgJson));
            } catch (Exception e) {
                log.warn("反序列化消息失败", e);
            }
        }
        return result;
    }

    /**
     * 从 MySQL 加载历史消息：读取 t_chat_message_compression（摘要）和 t_chat_message_raw（原始消息），
     * 按时间排序后合并，写入 Redis 并返回。
     */
    private List<String> warmupFromMysql(String conversationId, int lastN) {
        List<String[]> allRows = new ArrayList<>();

        // 1. 读取摘要记录
        try {
            String summarySql = "SELECT summary_content AS content, 'system' AS role, create_time " +
                    "FROM t_chat_message_compression WHERE conversation_id = ?";
            List<Map<String, Object>> summaries = jdbcTemplate.queryForList(summarySql, conversationId);
            for (Map<String, Object> row : summaries) {
                allRows.add(new String[]{row.get("content").toString(), row.get("role").toString(),
                        row.get("create_time").toString()});
            }
        } catch (Exception e) {
            log.debug("[RedisChatMemory] t_chat_message_compression 表查询失败（可能不存在）: {}", e.getMessage());
        }

        // 2. 读取原始消息
        try {
            String rawSql = "SELECT content, role, create_time FROM t_chat_message_raw WHERE conversation_id = ?";
            List<Map<String, Object>> raws = jdbcTemplate.queryForList(rawSql, conversationId);
            for (Map<String, Object> row : raws) {
                allRows.add(new String[]{row.get("content").toString(), row.get("role").toString(),
                        row.get("create_time").toString()});
            }
        } catch (Exception e) {
            log.warn("[RedisChatMemory] t_chat_message_raw 查询失败: {}", e.getMessage());
        }

        if (allRows.isEmpty()) {
            return List.of();
        }

        // 3. 按时间排序（摘要通常早于后续原始消息）
        allRows.sort((a, b) -> a[2].compareTo(b[2]));

        // 4. 取最后 lastN 条
        int start = Math.max(0, allRows.size() - lastN);
        List<String> result = new ArrayList<>();
        for (int i = start; i < allRows.size(); i++) {
            String[] row = allRows.get(i);
            Map<String, Object> msg = new HashMap<>();
            msg.put("msgId", IdUtil.getSnowflakeNextIdStr());
            msg.put("role", row[1]);
            msg.put("content", row[0]);
            msg.put("timestamp", System.currentTimeMillis());
            try {
                result.add(objectMapper.writeValueAsString(msg));
            } catch (JsonProcessingException e) {
                log.warn("序列化 warmup 消息失败", e);
            }
        }
        return result;
    }

    @Override
    public void clear(String conversationId) {
        String key = buildKey(conversationId);
        redisTemplate.delete(key);
        log.info("[RedisChatMemory] 清除会话 {}", conversationId);
    }

    private String serializeMessage(Message message) {
        String msgId = snowflake.nextIdStr();
        try {
            Map<String, Object> map = new HashMap<>();
            map.put("msgId", msgId);
            map.put("role", message.getMessageType().getValue());
            map.put("content", message.getText());
            map.put("timestamp", System.currentTimeMillis());
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("序列化消息失败", e);
        }
    }

    private Message deserializeMessage(String msgJson) throws JsonProcessingException {
        Map<String, Object> map = objectMapper.readValue(msgJson, Map.class);
        String role = map.get("role").toString();
        String content = map.get("content").toString();
        return switch (role) {
            case "user" -> new UserMessage(content);
            case "assistant" -> new AssistantMessage(content);
            case "system" -> new SystemMessage(content);
            default -> new UserMessage(content);
        };
    }
}