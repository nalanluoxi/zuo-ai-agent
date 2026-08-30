package com.example.zuoaiagent.knowledge.ingestion;

import com.example.zuoaiagent.knowledge.entity.KnowledgeDocumentDO;
import com.example.zuoaiagent.knowledge.mapper.KnowledgeDocumentMapper;
import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;

@Component
public class IngestionConsumer {

    private static final Logger log = LoggerFactory.getLogger(IngestionConsumer.class);

    private final DocumentIngestionService ingestionService;
    private final KnowledgeDocumentMapper documentMapper;

    @Value("${zuo.rabbitmq.queues.ingestion:ingestion.queue}")
    private String ingestionQueue;

    public IngestionConsumer(DocumentIngestionService ingestionService,
                             KnowledgeDocumentMapper documentMapper) {
        this.ingestionService = ingestionService;
        this.documentMapper = documentMapper;
    }

    @RabbitListener(queues = "${zuo.rabbitmq.queues.ingestion:ingestion.queue}")
    public void handle(Map<String, Object> message, Channel channel,
                       @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {
        try {
            Long docId = Long.valueOf(message.get("docId").toString());
            log.info("[入库消费者] 收到消息，docId={}", docId);

            KnowledgeDocumentDO doc = documentMapper.selectById(docId);
            if (doc == null) {
                log.warn("[入库消费者] 文档不存在，docId={}，确认消息", docId);
                channel.basicAck(deliveryTag, false);
                return;
            }

            ingestionService.ingest(docId);
            channel.basicAck(deliveryTag, false);
            log.info("[入库消费者] 入库完成，docId={}", docId);

        } catch (Exception e) {
            log.error("[入库消费者] 处理失败，消息将进入死信队列", e);
            try {
                channel.basicNack(deliveryTag, false, false);
            } catch (IOException ioException) {
                log.error("[入库消费者] nack失败", ioException);
            }
        }
    }
}