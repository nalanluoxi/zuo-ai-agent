package com.example.logmonitor.mq;
import com.example.logmonitor.config.RabbitMQConfig;
import com.example.logmonitor.entity.EventDO;
import com.example.logmonitor.mapper.EventMapper;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Slf4j
@Component
@RequiredArgsConstructor
public class EventConsumer {

    private final EventMapper eventMapper;

    @RabbitListener(queues = RabbitMQConfig.QUEUE_EVENT)
    public void onMessage(EventDTO dto, Channel channel,
                          @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {
        try {
            EventDO event = new EventDO();
            event.setEventType(dto.eventType());
            event.setSource(dto.source());
            event.setMessage(dto.message());
            event.setServiceName(dto.serviceName());
            event.setHost(dto.host());
            event.setEventTime(LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(dto.eventTimeMs()), ZoneId.systemDefault()));
            event.setMetadata(dto.metadata());
            eventMapper.insert(event);
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.error("处理 event 失败: {}", dto, e);
            try { channel.basicNack(deliveryTag, false, false); } catch (Exception ignored) {}
        }
    }

    public record EventDTO(
            String eventType, String source, String message,
            String serviceName, String host,
            long eventTimeMs, String metadata) {}
}
