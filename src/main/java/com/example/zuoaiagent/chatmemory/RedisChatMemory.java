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
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class RedisChatMemory implements ChatMemory {

    private static final Logger log = LoggerFactory.getLogger(RedisChatMemory.class);
    private static final String KEY_PREFIX = "chatmemory:queue:";
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final Snowflake snowflake = IdUtil.getSnowflake(1, 1);

    private final StringRedisTemplate redisTemplate;
    private final RabbitTemplate rabbitTemplate;
    private final int summaryStartTurns;
    private final int historyKeep;
    private final int sessionTtlMinutes;

    @Value("${zuo.rabbitmq.queues.chat-memory-persistence:chat.memory.persistence.queue}")
    private String persistenceQueue;

    @Value("${zuo.rabbitmq.queues.chat-memory-compression:chat.memory.compression.queue}")
    private String compressionQueue;

    public RedisChatMemory(StringRedisTemplate redisTemplate,
                           RabbitTemplate rabbitTemplate,
                           int summaryStartTurns,
                           int historyKeep,
                           int sessionTtlMinutes) {
        this.redisTemplate = redisTemplate;
        this.rabbitTemplate = rabbitTemplate;
        this.summaryStartTurns = summaryStartTurns;
        this.historyKeep = historyKeep;
        this.sessionTtlMinutes = sessionTtlMinutes;
    }

    @Override
    public void add(String conversationId, Message message) {
        add(conversationId, List.of(message));
    }

    @Override
    public void add(String conversationId, List<Message> messages) {
        String key = KEY_PREFIX + conversationId;
        for (Message message : messages) {
            String msgJson = serializeMessage(message);
            redisTemplate.opsForList().leftPush(key, msgJson);
        }
        redisTemplate.expire(key, sessionTtlMinutes, TimeUnit.MINUTES);

        Long queueLength = redisTemplate.opsForList().size(key);
        if (queueLength != null && queueLength > summaryStartTurns * 2L) {
            triggerCompression(conversationId, key);
        }

        for (Message message : messages) {
            sendPersistenceMessage(conversationId, message);
        }
    }

    private void triggerCompression(String conversationId, String key) {
        List<String> tailMessages = redisTemplate.opsForList().range(key, -summaryStartTurns, -1);
        if (tailMessages == null || tailMessages.isEmpty()) {
            return;
        }
        List<String> snapshotMsgIds = new ArrayList<>();
        for (String msgJson : tailMessages) {
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
        String key = KEY_PREFIX + conversationId;
        List<String> messages = redisTemplate.opsForList().range(key, 0, lastN - 1);
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }
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

    @Override
    public void clear(String conversationId) {
        String key = KEY_PREFIX + conversationId;
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