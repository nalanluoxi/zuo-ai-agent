package com.example.zuoaiagent.chatmemory;

import com.example.zuoaiagent.chat.RoutingChatService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
public class ChatMemoryCompressionConsumer {

    private static final Logger log = LoggerFactory.getLogger(ChatMemoryCompressionConsumer.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final String KEY_PREFIX = "chatmemory:queue:";
    private static final String LOCK_PREFIX = "chatmemory:lock:";

    private final StringRedisTemplate redisTemplate;
    private final RedissonClient redissonClient;
    private final RoutingChatService routingChatService;

    private static final String COMPRESS_LUA_SCRIPT =
            "local key = KEYS[1] " +
            "local snapshotCount = tonumber(ARGV[1]) " +
            "local summaryJson = ARGV[2] " +
            "local snapshotMsgIds = {} " +
            "for i = 3, #ARGV do table.insert(snapshotMsgIds, ARGV[i]) end " +
            "local currentMsgs = redis.call('LRANGE', key, -snapshotCount, -1) " +
            "for i, msgJson in ipairs(currentMsgs) do " +
            "  local ok, msg = pcall(cjson.decode, msgJson) " +
            "  if not ok then return 0 end " +
            "  local currentId = msg['msgId'] " +
            "  local expectedId = snapshotMsgIds[i] " +
            "  if currentId ~= expectedId then return 0 end " +
            "end " +
            "redis.call('LTRIM', key, 0, -snapshotCount - 1) " +
            "redis.call('LPUSH', key, summaryJson) " +
            "return 1";

    public ChatMemoryCompressionConsumer(StringRedisTemplate redisTemplate,
                                          RedissonClient redissonClient,
                                          RoutingChatService routingChatService) {
        this.redisTemplate = redisTemplate;
        this.redissonClient = redissonClient;
        this.routingChatService = routingChatService;
    }

    @SuppressWarnings("unchecked")
    @RabbitListener(queues = "${zuo.rabbitmq.queues.chat-memory-compression:chat.memory.compression.queue}")
    public void handle(Map<String, Object> message, Channel channel,
                       @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {
        String conversationId = message.get("conversationId").toString();
        List<String> snapshotMsgIds = (List<String>) message.get("snapshotMsgIds");
        String lockKey = LOCK_PREFIX + conversationId;

        RLock lock = redissonClient.getLock(lockKey);
        try {
            if (!lock.tryLock(0, TimeUnit.SECONDS)) {
                log.debug("[压缩消费者] 未获取到锁，跳过，conversationId={}", conversationId);
                channel.basicAck(deliveryTag, false);
                return;
            }

            String key = KEY_PREFIX + conversationId;
            int snapshotCount = snapshotMsgIds.size();
            List<String> currentTail = redisTemplate.opsForList().range(key, -snapshotCount, -1);
            if (currentTail == null || currentTail.size() != snapshotCount) {
                log.debug("[压缩消费者] 队列长度已变化，跳过压缩，conversationId={}", conversationId);
                channel.basicAck(deliveryTag, false);
                return;
            }

            String summary = generateSummary(conversationId, key, snapshotCount);
            if (summary == null) {
                log.warn("[压缩消费者] 摘要生成失败，跳过压缩，conversationId={}", conversationId);
                channel.basicAck(deliveryTag, false);
                return;
            }

            String summaryJson = "{\"msgId\":\"summary_" + System.currentTimeMillis() +
                    "\",\"role\":\"system\",\"content\":\"[对话摘要] " +
                    escapeJson(summary) + "\",\"timestamp\":" + System.currentTimeMillis() + "}";

            Object[] args = new Object[2 + snapshotMsgIds.size()];
            args[0] = String.valueOf(snapshotCount);
            args[1] = summaryJson;
            for (int i = 0; i < snapshotMsgIds.size(); i++) {
                args[2 + i] = snapshotMsgIds.get(i);
            }

            DefaultRedisScript<Long> script = new DefaultRedisScript<>(COMPRESS_LUA_SCRIPT, Long.class);
            Long result = redisTemplate.execute(script, List.of(key), args);

            if (result != null && result == 1) {
                log.info("[压缩消费者] 压缩完成，conversationId={}, 移除{}条消息", conversationId, snapshotCount);
            } else {
                log.info("[压缩消费者] 压缩已跳过(Lua校验失败)，conversationId={}", conversationId);
            }

            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.error("[压缩消费者] 处理失败", e);
            try {
                channel.basicNack(deliveryTag, false, false);
            } catch (IOException ioException) {
                log.error("[压缩消费者] nack失败", ioException);
            }
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private String generateSummary(String conversationId, String key, int count) {
        List<String> messages = redisTemplate.opsForList().range(key, -count, -1);
        if (messages == null || messages.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (String msgJson : messages) {
            try {
                Map<String, Object> map = objectMapper.readValue(msgJson, Map.class);
                sb.append(map.get("role")).append(": ").append(map.get("content")).append("\n");
            } catch (Exception e) {
                log.warn("解析消息失败", e);
            }
        }
        String prompt = "请对以下对话内容进行简洁摘要（不超过200字），保留关键信息和结论：\n\n" + sb;
        try {
            return routingChatService.chat(prompt, null, null, null);
        } catch (Exception e) {
            log.warn("[压缩消费者] 摘要生成失败: {}", e.getMessage());
            return null;
        }
    }

    private String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }
}