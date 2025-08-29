package com.raketman.shortlisting.config;

import com.raketman.shortlisting.batch.CVItemProcessor;
import com.raketman.shortlisting.batch.CVItemReader;
import com.raketman.shortlisting.batch.CVItemWriter;
import com.raketman.shortlisting.batch.JobCompletionNotificationListener;
import com.raketman.shortlisting.entity.CV;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
public class CVBatchConfig {

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private CVItemProcessor cvItemProcessor;

    @Autowired
    private CVItemWriter cvItemWriter;

    @Autowired
    private JobCompletionNotificationListener jobCompletionListener;

    /**
     * Task executor for parallel processing
     * Configured for optimal concurrency based on system resources
     */
    @Bean(name = "cvTaskExecutor")
    public TaskExecutor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // Configure thread pool for concurrent processing
        int corePoolSize = Runtime.getRuntime().availableProcessors();
        int maxPoolSize = Math.max(10, corePoolSize * 2); // At least 10, max 2x cores

        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("cv-batch-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();

        return executor;
    }

    /**
     * CV Item Reader - reads files from directory
     */
    @Bean
    @StepScope
    public CVItemReader cvItemReader(@Value("#{jobParameters['inputDir']}") String inputDir) {
        CVItemReader reader = new CVItemReader();
        reader.setInputDirectory(inputDir);
        return reader;
    }

    /**
     * CV Processing Step - processes CVs concurrently
     */
    @Bean
    public Step cvProcessingStep() {
        return new StepBuilder("cvProcessingStep", jobRepository)
                .<java.io.File, CV>chunk(10, transactionManager) // Process in chunks of 10
                .reader(cvItemReader(null))
                .processor(cvItemProcessor)
                .writer(cvItemWriter)
                .taskExecutor(taskExecutor())
                .throttleLimit(20) // Max 20 concurrent threads
                .faultTolerant()
                .skipLimit(50) // Skip up to 50 failed items
                .skip(Exception.class)
                .build();
    }

    /**
     * Main CV Processing Job
     */
    @Bean
    public Job cvProcessingJob() {
        return new JobBuilder("cvProcessingJob", jobRepository)
                .incrementer(new RunIdIncrementer())
                .listener(jobCompletionListener)
                .start(cvProcessingStep())
                .build();
    }

    /**
     * Partitioned step for even better performance on large datasets
     */
    @Bean
    public Step partitionedCvProcessingStep() {
        return new StepBuilder("partitionedCvProcessingStep", jobRepository)
                .partitioner("cvProcessingStep", partitioner())
                .step(cvProcessingStep())
                .gridSize(4) // 4 partitions
                .taskExecutor(taskExecutor())
                .build();
    }

    /**
     * Partitioner for distributing work across multiple threads
     */
    @Bean
    public CVPartitioner partitioner() {
        return new CVPartitioner();
    }

    /**
     * Job for partitioned processing (for very large datasets)
     */
    @Bean
    public Job partitionedCvProcessingJob() {
        return new JobBuilder("partitionedCvProcessingJob", jobRepository)
                .incrementer(new RunIdIncrementer())
                .listener(jobCompletionListener)
                .start(partitionedCvProcessingStep())
                .build();
    }
}
