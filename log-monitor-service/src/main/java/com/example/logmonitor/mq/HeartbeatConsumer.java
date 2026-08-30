package com.example.logmonitor.mq;
import com.example.logmonitor.config.RabbitMQConfig;
import com.example.logmonitor.entity.HeartbeatDO;
import com.example.logmonitor.mapper.HeartbeatMapper;
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
public class HeartbeatConsumer {

    private final HeartbeatMapper heartbeatMapper;

    @RabbitListener(queues = RabbitMQConfig.QUEUE_HEARTBEAT)
    public void onMessage(HeartbeatDTO dto, Channel channel,
                          @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {
        try {
            HeartbeatDO hb = new HeartbeatDO();
            hb.setServiceName(dto.serviceName());
            hb.setHost(dto.host());
            hb.setStatus(dto.status() != null ? dto.status() : "HEALTHY");
            hb.setCpuUsage(dto.cpuUsage());
            hb.setMemoryUsage(dto.memoryUsage());
            hb.setActiveThreads(dto.activeThreads());
            hb.setGcCount(dto.gcCount());
            hb.setHeartbeatTime(LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(dto.heartbeatTimeMs()), ZoneId.systemDefault()));
            heartbeatMapper.insert(hb);
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.error("处理 heartbeat 失败: {}", dto, e);
            try { channel.basicNack(deliveryTag, false, false); } catch (Exception ignored) {}
        }
    }

    public record HeartbeatDTO(
            String serviceName, String host, String status,
            Double cpuUsage, Double memoryUsage,
            Integer activeThreads, Long gcCount,
            long heartbeatTimeMs) {}
}
