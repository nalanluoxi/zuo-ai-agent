package com.example.zuoaiagent.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class RabbitMQConfig {

    @Value("${zuo.rabbitmq.exchange:zuo.ai.agent}")
    private String exchangeName;

    @Value("${zuo.rabbitmq.queues.ingestion:ingestion.queue}")
    private String ingestionQueueName;

    @Value("${zuo.rabbitmq.queues.chat-memory-persistence:chat.memory.persistence.queue}")
    private String chatMemoryPersistenceQueueName;

    @Value("${zuo.rabbitmq.queues.chat-memory-compression:chat.memory.compression.queue}")
    private String chatMemoryCompressionQueueName;

    private static final String DLX_SUFFIX = ".dlx";
    private static final String DLQ_SUFFIX = ".dlq";

    @Bean
    public DirectExchange zuoExchange() {
        return new DirectExchange(exchangeName);
    }

    @Bean
    public Queue ingestionQueue() {
        return QueueBuilder.durable(ingestionQueueName)
                .withArgument("x-dead-letter-exchange", exchangeName + DLX_SUFFIX)
                .withArgument("x-dead-letter-routing-key", ingestionQueueName + DLQ_SUFFIX)
                .build();
    }

    @Bean
    public Queue ingestionDlqQueue() {
        return new Queue(ingestionQueueName + DLQ_SUFFIX, true);
    }

    @Bean
    public DirectExchange dlxExchange() {
        return new DirectExchange(exchangeName + DLX_SUFFIX);
    }

    @Bean
    public Binding ingestionBinding() {
        return BindingBuilder.bind(ingestionQueue()).to(zuoExchange()).with(ingestionQueueName);
    }

    @Bean
    public Binding ingestionDlqBinding() {
        return BindingBuilder.bind(ingestionDlqQueue()).to(dlxExchange()).with(ingestionQueueName + DLQ_SUFFIX);
    }

    @Bean
    public Queue chatMemoryPersistenceQueue() {
        return QueueBuilder.durable(chatMemoryPersistenceQueueName)
                .withArgument("x-dead-letter-exchange", exchangeName + DLX_SUFFIX)
                .withArgument("x-dead-letter-routing-key", chatMemoryPersistenceQueueName + DLQ_SUFFIX)
                .build();
    }

    @Bean
    public Queue chatMemoryPersistenceDlqQueue() {
        return new Queue(chatMemoryPersistenceQueueName + DLQ_SUFFIX, true);
    }

    @Bean
    public Binding chatMemoryPersistenceBinding() {
        return BindingBuilder.bind(chatMemoryPersistenceQueue()).to(zuoExchange()).with(chatMemoryPersistenceQueueName);
    }

    @Bean
    public Binding chatMemoryPersistenceDlqBinding() {
        return BindingBuilder.bind(chatMemoryPersistenceDlqQueue()).to(dlxExchange()).with(chatMemoryPersistenceQueueName + DLQ_SUFFIX);
    }

    @Bean
    public Queue chatMemoryCompressionQueue() {
        return QueueBuilder.durable(chatMemoryCompressionQueueName)
                .withArgument("x-dead-letter-exchange", exchangeName + DLX_SUFFIX)
                .withArgument("x-dead-letter-routing-key", chatMemoryCompressionQueueName + DLQ_SUFFIX)
                .build();
    }

    @Bean
    public Queue chatMemoryCompressionDlqQueue() {
        return new Queue(chatMemoryCompressionQueueName + DLQ_SUFFIX, true);
    }

    @Bean
    public Binding chatMemoryCompressionBinding() {
        return BindingBuilder.bind(chatMemoryCompressionQueue()).to(zuoExchange()).with(chatMemoryCompressionQueueName);
    }

    @Bean
    public Binding chatMemoryCompressionDlqBinding() {
        return BindingBuilder.bind(chatMemoryCompressionDlqQueue()).to(dlxExchange()).with(chatMemoryCompressionQueueName + DLQ_SUFFIX);
    }
}