package com.example.logmonitor.mq;
import com.example.logmonitor.config.RabbitMQConfig;
import com.example.logmonitor.entity.TraceSpanDO;
import com.example.logmonitor.mapper.TraceSpanMapper;
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
public class TraceConsumer {

    private final TraceSpanMapper traceSpanMapper;

    @RabbitListener(queues = RabbitMQConfig.QUEUE_TRACE)
    public void onMessage(TraceSpanDTO dto, Channel channel,
                          @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {
        try {
            TraceSpanDO span = new TraceSpanDO();
            span.setTraceId(dto.traceId());
            span.setSpanId(dto.spanId());
            span.setParentSpanId(dto.parentSpanId());
            span.setOperationName(dto.operationName());
            span.setServiceName(dto.serviceName());
            span.setStartTime(LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(dto.startTimeMs()), ZoneId.systemDefault()));
            span.setDurationMs(dto.durationMs());
            span.setStatus(dto.status() != null ? dto.status() : "OK");
            span.setTags(dto.tags());
            traceSpanMapper.insert(span);
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.error("处理 trace span 失败: {}", dto, e);
            try { channel.basicNack(deliveryTag, false, false); } catch (Exception ignored) {}
        }
    }

    public record TraceSpanDTO(
            String traceId, String spanId, String parentSpanId,
            String operationName, String serviceName,
            long startTimeMs, Double durationMs, String status, String tags) {}
}
