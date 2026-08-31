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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.jdbc.core.JdbcTemplate;
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
    // KEY_PREFIX 必须与 RedisChatMemory 一致
    private static final String KEY_PREFIX = "chatmemory:";
    private static final String LOCK_PREFIX = "chatmemory:lock:compression:";

    private final StringRedisTemplate redisTemplate;
    private final RedissonClient redissonClient;
    private final RoutingChatService routingChatService;
    private final JdbcTemplate jdbcTemplate;

    /**
     * Lua 脚本：原子性地校验队头（最旧的）消息 ID 是否匹配，匹配则删除队头并 LPUSH 摘要到队头。
     *
     * KEYS[1] = chatmemory:{conversationId}
     * ARGV[1] = snapshotCount（要压缩的消息数量）
     * ARGV[2] = summaryJson（摘要 JSON）
     * ARGV[3...] = snapshotMsgIds（快照的消息 ID 列表）
     *
     * 返回值：1=成功，0=队头已变化（不压缩）
     */
    private static final String COMPRESS_LUA_SCRIPT =
            "local key = KEYS[1]\n" +
            "local snapshotCount = tonumber(ARGV[1])\n" +
            "local summaryJson = ARGV[2]\n" +
            "local snapshotMsgIds = {}\n" +
            "for i = 3, #ARGV do table.insert(snapshotMsgIds, ARGV[i]) end\n" +
            "local currentMsgs = redis.call('LRANGE', key, 0, snapshotCount - 1)\n" +
            "if #currentMsgs ~= snapshotCount then return 0 end\n" +
            "for i, msgJson in ipairs(currentMsgs) do\n" +
            "  local ok, msg = pcall(cjson.decode, msgJson)\n" +
            "  if not ok then return 0 end\n" +
            "  local currentId = msg['msgId']\n" +
            "  local expectedId = snapshotMsgIds[i]\n" +
            "  if currentId ~= expectedId then return 0 end\n" +
            "end\n" +
            "redis.call('LTRIM', key, snapshotCount, -1)\n" +
            "redis.call('LPUSH', key, summaryJson)\n" +
            "return 1";

    public ChatMemoryCompressionConsumer(StringRedisTemplate redisTemplate,
                                          RedissonClient redissonClient,
                                          RoutingChatService routingChatService,
                                          JdbcTemplate jdbcTemplate) {
        this.redisTemplate = redisTemplate;
        this.redissonClient = redissonClient;
        this.routingChatService = routingChatService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @SuppressWarnings("unchecked")
    @RabbitListener(queues = "${zuo.rabbitmq.queues.chat-memory-compression:chat.memory.compression.queue}")
    public void handle(Map<String, Object> message, Channel channel,
                       @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {
        try {
            String conversationId = message.get("conversationId").toString();
            List<String> snapshotMsgIds = (List<String>) message.get("snapshotMsgIds");
            int snapshotCount = snapshotMsgIds.size();
            String key = KEY_PREFIX + conversationId;

            // ===== 第1步：读取队尾消息（无锁） =====
            List<String> tailMessages = redisTemplate.opsForList().range(key, -snapshotCount, -1);
            if (tailMessages == null || tailMessages.size() != snapshotCount) {
                log.info("[压缩消费者] 队列长度已变化，跳过，conversationId={}", conversationId);
                channel.basicAck(deliveryTag, false);
                return;
            }

            // 提前收集摘要所需内容（即使后面 Lua 校验失败，这里读到的内容也已够用）
            StringBuilder sb = new StringBuilder();
            for (String msgJson : tailMessages) {
                Map<String, Object> map = objectMapper.readValue(msgJson, Map.class);
                sb.append(map.get("role")).append(": ").append(map.get("content")).append("\n");
            }

            // ===== 第2步：调用 AI 生成摘要（无锁，耗时几秒） =====
            String prompt = "请对以下对话内容进行简洁摘要（不超过200字），保留关键信息和结论：\n\n" + sb;
            String summary;
            try {
                summary = routingChatService.chat(prompt, null, null, null);
            } catch (Exception e) {
                log.warn("[压缩消费者] 摘要生成失败，跳过，conversationId={}, error={}", conversationId, e.getMessage());
                channel.basicAck(deliveryTag, false);
                return;
            }

            String summaryJson = "{\"msgId\":\"summary_" + System.currentTimeMillis() +
                    "\",\"role\":\"system\",\"content\":\"[对话摘要] " +
                    escapeJson(summary) + "\",\"timestamp\":" + System.currentTimeMillis() + "}";

            // ===== 第3步：加锁 → 校验队尾 → 修改 Redis + MySQL → 释放锁 =====
            String lockKey = LOCK_PREFIX + conversationId;
            RLock lock = redissonClient.getLock(lockKey);
            if (!lock.tryLock(5, 10, TimeUnit.SECONDS)) {
                log.debug("[压缩消费者] 未获取到锁，跳过，conversationId={}", conversationId);
                channel.basicAck(deliveryTag, false);
                return;
            }

            try {
                // 原子校验队尾 msgId 是否仍与快照匹配，匹配则 LTRIM + LPUSH
                Object[] args = new Object[2 + snapshotMsgIds.size()];
                args[0] = String.valueOf(snapshotCount);
                args[1] = summaryJson;
                for (int i = 0; i < snapshotMsgIds.size(); i++) {
                    args[2 + i] = snapshotMsgIds.get(i);
                }
                DefaultRedisScript<Long> script = new DefaultRedisScript<>(COMPRESS_LUA_SCRIPT, Long.class);
                Long result = redisTemplate.execute(script, List.of(key), args);

                if (result != null && result == 1) {
                    // Lua 已原子完成：弹出旧消息 + 压入摘要
                    // 现在写入 MySQL 摘要表
                    persistSummaryToMysql(conversationId, summary, snapshotMsgIds);
                    log.info("[压缩消费者] 压缩完成，conversationId={}, 移除{}条消息", conversationId, snapshotCount);
                } else {
                    log.info("[压缩消费者] 队尾已变化，丢弃本次摘要，conversationId={}", conversationId);
                }

                channel.basicAck(deliveryTag, false);
            } finally {
                if (lock.isHeldByCurrentThread()) {
                    lock.unlock();
                }
            }
        } catch (Exception e) {
            log.error("[压缩消费者] 处理失败", e);
            try {
                channel.basicNack(deliveryTag, false, false);
            } catch (IOException ioException) {
                log.error("[压缩消费者] nack失败", ioException);
            }
        }
    }

    /**
     * 将摘要持久化到 MySQL t_chat_message_compression 表
     */
    private void persistSummaryToMysql(String conversationId, String summary, List<String> sourceMsgIds) {
        try {
            long id = cn.hutool.core.util.IdUtil.getSnowflakeNextId();
            String sourceIds = String.join(",", sourceMsgIds);
            jdbcTemplate.update(
                "INSERT INTO t_chat_message_compression (id, conversation_id, summary_content, source_msg_ids, source_count) " +
                "VALUES (?, ?, ?, ?, ?)",
                id, conversationId, summary, sourceIds, sourceMsgIds.size()
            );
            log.debug("[压缩消费者] 摘要已写入 MySQL，conversationId={}", conversationId);
        } catch (Exception e) {
            log.warn("[压缩消费者] 写入 MySQL 失败（非致命）: {}", e.getMessage());
            // 不抛异常，MySQL 写入失败不影响主流程
        }
    }

    private String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }
}