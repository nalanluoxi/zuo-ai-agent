package com.example.zuoaiagent.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 异步任务线程池配置
 *
 * <p>为文档入库流水线提供独立的线程池 {@code ingestionExecutor}，
 * 避免占用 Spring 默认异步执行器影响其他业务。
 *
 * <p>{@link EnableAsync} 注解开启 Spring 的 {@code @Async} 注解支持。
 */
@EnableAsync
@Configuration
public class AsyncConfig {

    private static final Logger log = LoggerFactory.getLogger(AsyncConfig.class);

    /**
     * 文档入库专用线程池。
     *
     * <p>配置说明：
     * <ul>
     *   <li>corePoolSize=2：常驻线程数，满足低并发场景</li>
     *   <li>maxPoolSize=8：最大线程数，突发上传时扩展</li>
     *   <li>queueCapacity=100：任务队列，防止突发请求丢失</li>
     *   <li>RejectedPolicy=CallerRunsPolicy：队列满时降级为同步执行，保证不丢任务</li>
     * </ul>
     *
     * @return 配置好的线程池执行器
     */
    @Bean("ingestionExecutor")
    public Executor ingestionExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("ingestion-");
        // 队列满时由调用方线程同步执行，保证不丢任务
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        log.info("文档入库线程池初始化完成: core=2, max=8, queue=100");
        return executor;
    }

    /**
     * 多通道并行检索专用线程池。
     *
     * <p>配置说明：
     * <ul>
     *   <li>corePoolSize=4：每次请求最多 2 个通道并行，预留余量</li>
     *   <li>maxPoolSize=16：高并发时扩展</li>
     *   <li>queueCapacity=200：防止突发丢失</li>
     *   <li>RejectedPolicy=CallerRunsPolicy：降级为同步执行</li>
     * </ul>
     *
     * @return 配置好的线程池执行器
     */
    @Bean("retrievalExecutor")
    public Executor retrievalExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("retrieval-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        log.info("并行检索线程池初始化完成: core=4, max=16, queue=200");
        return executor;
    }
}
