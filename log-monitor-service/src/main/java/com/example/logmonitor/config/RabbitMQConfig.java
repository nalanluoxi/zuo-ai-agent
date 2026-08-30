package com.example.logmonitor.config;
import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    // Exchanges
    public static final String EXCHANGE_TRACE = "monitor.trace";
    public static final String EXCHANGE_EVENT = "monitor.event";
    public static final String EXCHANGE_HEARTBEAT = "monitor.heartbeat";
    public static final String EXCHANGE_LOG = "monitor.log";

    // Queues
    public static final String QUEUE_TRACE = "monitor.trace.queue";
    public static final String QUEUE_EVENT = "monitor.event.queue";
    public static final String QUEUE_HEARTBEAT = "monitor.heartbeat.queue";
    public static final String QUEUE_LOG = "monitor.log.queue";

    // Routing keys
    public static final String RK_TRACE = "span";
    public static final String RK_EVENT = "event";
    public static final String RK_HEARTBEAT = "heartbeat";
    public static final String RK_LOG = "log";

    @Bean
    public Jackson2JsonMessageConverter jsonConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public TopicExchange traceExchange() { return new TopicExchange(EXCHANGE_TRACE); }

    @Bean
    public TopicExchange eventExchange() { return new TopicExchange(EXCHANGE_EVENT); }

    @Bean
    public TopicExchange heartbeatExchange() { return new TopicExchange(EXCHANGE_HEARTBEAT); }

    @Bean
    public TopicExchange logExchange() { return new TopicExchange(EXCHANGE_LOG); }

    @Bean
    public Queue traceQueue() { return QueueBuilder.durable(QUEUE_TRACE).build(); }

    @Bean
    public Queue eventQueue() { return QueueBuilder.durable(QUEUE_EVENT).build(); }

    @Bean
    public Queue heartbeatQueue() { return QueueBuilder.durable(QUEUE_HEARTBEAT).build(); }

    @Bean
    public Queue logQueue() { return QueueBuilder.durable(QUEUE_LOG).build(); }

    @Bean
    public Binding traceBinding() { return BindingBuilder.bind(traceQueue()).to(traceExchange()).with(RK_TRACE); }

    @Bean
    public Binding eventBinding() { return BindingBuilder.bind(eventQueue()).to(eventExchange()).with(RK_EVENT); }

    @Bean
    public Binding heartbeatBinding() { return BindingBuilder.bind(heartbeatQueue()).to(heartbeatExchange()).with(RK_HEARTBEAT); }

    @Bean
    public Binding logBinding() { return BindingBuilder.bind(logQueue()).to(logExchange()).with(RK_LOG); }
}
