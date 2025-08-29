package com.raketman.shortlisting.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Configuration for asynchronous processing
 * Optimized for CV processing workloads with proper thread management
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    private static final Logger logger = LoggerFactory.getLogger(AsyncConfig.class);

    /**
     * Task executor for general async operations
     */
    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        int corePoolSize = Math.max(2, Runtime.getRuntime().availableProcessors() / 2);
        int maxPoolSize = Runtime.getRuntime().availableProcessors() * 2;

        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("cv-async-");
        executor.setKeepAliveSeconds(60);

        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());

        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);

        executor.initialize();

        logger.info("Initialized async task executor with core pool size: {}, max pool size: {}",
                corePoolSize, maxPoolSize);

        return executor;
    }

    /**
     * Dedicated executor for CV processing operations
     */
    @Bean(name = "cvProcessingExecutor")
    public Executor cvProcessingExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // Optimized for CV processing - more threads for I/O intensive operations
        int corePoolSize = Runtime.getRuntime().availableProcessors();
        int maxPoolSize = Math.max(20, corePoolSize * 3);

        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("cv-processing-");
        executor.setKeepAliveSeconds(120);

        // Custom rejection policy with logging
        executor.setRejectedExecutionHandler((runnable, threadPoolExecutor) -> {
            logger.warn("CV processing task rejected. Queue full. Running in caller thread.");
            if (!threadPoolExecutor.isShutdown()) {
                runnable.run();
            }
        });

        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);

        executor.initialize();

        logger.info("Initialized CV processing executor with core pool size: {}, max pool size: {}",
                corePoolSize, maxPoolSize);

        return executor;
    }

    @Bean(name = "batchExecutor")
    public Executor batchExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // Conservative settings for batch operations
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("cv-batch-");
        executor.setKeepAliveSeconds(300);

        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(120);

        executor.initialize();

        logger.info("Initialized batch executor for long-running batch operations");

        return executor;
    }

}
