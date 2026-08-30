package com.example.zuoaiagent.chatmemory;

import cn.hutool.core.lang.Snowflake;
import cn.hutool.core.util.IdUtil;
import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;

@Component
public class ChatMemoryPersistenceConsumer {

    private static final Logger log = LoggerFactory.getLogger(ChatMemoryPersistenceConsumer.class);
    private static final Snowflake snowflake = IdUtil.getSnowflake(1, 2);

    private final JdbcTemplate jdbcTemplate;

    public ChatMemoryPersistenceConsumer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @RabbitListener(queues = "${zuo.rabbitmq.queues.chat-memory-persistence:chat.memory.persistence.queue}")
    public void handle(Map<String, Object> message, Channel channel,
                       @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {
        try {
            String msgId = message.get("msgId").toString();
            String conversationId = message.get("conversationId").toString();
            String role = message.get("role").toString();
            String content = message.get("content").toString();

            jdbcTemplate.update(
                    "INSERT INTO t_chat_message_raw (id, msg_id, conversation_id, role, content) VALUES (?, ?, ?, ?, ?)",
                    snowflake.nextId(), msgId, conversationId, role, content
            );

            channel.basicAck(deliveryTag, false);
            log.debug("[持久化消费者] 消息已落库，msgId={}", msgId);
        } catch (Exception e) {
            log.error("[持久化消费者] 处理失败，msgId={}", message.get("msgId"), e);
            try {
                channel.basicNack(deliveryTag, false, false);
            } catch (IOException ioException) {
                log.error("[持久化消费者] nack失败", ioException);
            }
        }
    }
}