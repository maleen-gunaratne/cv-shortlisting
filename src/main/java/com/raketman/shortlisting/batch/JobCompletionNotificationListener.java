package com.raketman.shortlisting.batch;

import com.raketman.shortlisting.entity.CV;
import com.raketman.shortlisting.repository.CVRepository;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.batch.core.StepExecution;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class JobCompletionNotificationListener implements JobExecutionListener {

    private static final Logger logger = LoggerFactory.getLogger(JobCompletionNotificationListener.class);

    @Autowired
    private CVRepository cvRepository;

    @Override
    public void beforeJob(JobExecution jobExecution) {
        logger.info("Starting Job: name={}, executionId={}, parameters={}, startTime={}",
                jobExecution.getJobInstance().getJobName(),
                jobExecution.getId(),
                jobExecution.getJobParameters().getParameters(),
                jobExecution.getStartTime());

        logSystemInformation();
    }

    @Override
    public void afterJob(JobExecution jobExecution) {
        try {
            Duration duration = calculateJobDuration(jobExecution);
            JobCompletionStatistics stats = collectJobStatistics(jobExecution);

            logger.info("Job Completed: name={}, executionId={}, status={}, endTime={}, duration={}s",
                    jobExecution.getJobInstance().getJobName(),
                    jobExecution.getId(),
                    jobExecution.getStatus(),
                    jobExecution.getEndTime(),
                    duration.toSeconds());

            logJobCompletionDetails(jobExecution, stats, duration);

            if (jobExecution.getStatus() == BatchStatus.COMPLETED) {
                handleSuccessfulJobCompletion(stats);
            } else {
                handleFailedJobCompletion(jobExecution, stats);
            }

            performPostJobCleanup(jobExecution);

        } catch (Exception e) {
            logger.error("Job completion error: jobExecutionId={}, message={}", jobExecution.getId(), e.getMessage(), e);
        }
    }

    private Duration calculateJobDuration(JobExecution jobExecution) {
        LocalDateTime startTime = jobExecution.getStartTime();
        LocalDateTime endTime = jobExecution.getEndTime();
        return (startTime != null && endTime != null) ? Duration.between(startTime, endTime) : Duration.ZERO;
    }

    private JobCompletionStatistics collectJobStatistics(JobExecution jobExecution) {
        JobCompletionStatistics stats = new JobCompletionStatistics();
        String batchId = getBatchId(jobExecution);
        stats.setBatchId(batchId);

        for (StepExecution stepExecution : jobExecution.getStepExecutions()) {
            stats.addReadCount(stepExecution.getReadCount());
            stats.addWriteCount(stepExecution.getWriteCount());
            stats.addSkipCount(stepExecution.getSkipCount());
            stats.addProcessCount(stepExecution.getWriteCount());
        }

        if (batchId != null) {
            List<CV> batchCVs = cvRepository.findByBatchId(batchId);
            stats.setTotalCVs(batchCVs.size());

            Map<CV.CVStatus, Long> statusCounts = batchCVs.stream()
                    .collect(Collectors.groupingBy(CV::getStatus, Collectors.counting()));

            stats.setShortlistedCount(statusCounts.getOrDefault(CV.CVStatus.SHORTLISTED, 0L));
            stats.setDuplicateCount(statusCounts.getOrDefault(CV.CVStatus.DUPLICATE, 0L));
            stats.setRejectedCount(statusCounts.getOrDefault(CV.CVStatus.REJECTED, 0L));
            stats.setErrorCount(statusCounts.getOrDefault(CV.CVStatus.ERROR, 0L));

            batchCVs.stream()
                    .filter(cv -> cv.getProcessingTimeMs() != null)
                    .mapToLong(CV::getProcessingTimeMs)
                    .average()
                    .ifPresent(stats::setAverageProcessingTimeMs);

            Duration duration = calculateJobDuration(jobExecution);
            if (!duration.isZero() && stats.getTotalCVs() > 0) {
                double throughputPerSecond = (double) stats.getTotalCVs() / Math.max(duration.toSeconds(), 1);
                stats.setThroughputPerSecond(throughputPerSecond);
            }
        }

        return stats;
    }

    private void logJobCompletionDetails(JobExecution jobExecution, JobCompletionStatistics stats, Duration duration) {
        logger.info("Summary: totalCVs={}, shortlisted={} ({}%), duplicates={} ({}%), rejected={} ({}%), errors={} ({}%), " +
                        "avgProcessingTime={}ms, throughput={} CV/s, read={}, written={}, skipped={}",
                stats.getTotalCVs(),
                stats.getShortlistedCount(), String.format("%.1f", stats.getShortlistedPercentage()),
                stats.getDuplicateCount(), String.format("%.1f", stats.getDuplicatePercentage()),
                stats.getRejectedCount(), String.format("%.1f", stats.getRejectedPercentage()),
                stats.getErrorCount(), String.format("%.1f", stats.getErrorPercentage()),
                String.format("%.2f", stats.getAverageProcessingTimeMs()),
                String.format("%.2f", stats.getThroughputPerSecond()),
                stats.getReadCount(), stats.getWriteCount(), stats.getSkipCount());

        for (StepExecution stepExecution : jobExecution.getStepExecutions()) {
            logger.debug("Step Summary: step={}, read={}, written={}, skipped={}",
                    stepExecution.getStepName(),
                    stepExecution.getReadCount(),
                    stepExecution.getWriteCount(),
                    stepExecution.getSkipCount());
        }

        String exitDesc = jobExecution.getExitStatus().getExitDescription();
        if (exitDesc != null && !exitDesc.isBlank()) {
            logger.warn("Exit Description: {}", exitDesc);
        }

        if (!jobExecution.getFailureExceptions().isEmpty()) {
            jobExecution.getFailureExceptions().forEach(exception ->
                    logger.error("Failure: {}", exception.getMessage(), exception));
        }
    }

    private void handleSuccessfulJobCompletion(JobCompletionStatistics stats) {
        logger.info("Job marked as COMPLETED, batchId={}", stats.getBatchId());
        updateBatchJobStatus(stats.getBatchId(), "COMPLETED", stats);
        generateCompletionReport(stats, true);
    }

    private void handleFailedJobCompletion(JobExecution jobExecution, JobCompletionStatistics stats) {
        logger.error("Job marked as FAILED, batchId={}", stats.getBatchId());
        updateBatchJobStatus(stats.getBatchId(), "FAILED", stats);
        generateCompletionReport(stats, false);
        logFailureAnalysis(jobExecution);
    }

    private void performPostJobCleanup(JobExecution jobExecution) {
        try {
            logger.debug("Post-job cleanup completed, jobExecutionId={}", jobExecution.getId());
        } catch (Exception e) {
            logger.warn("Post-job cleanup error: jobExecutionId={}, message={}", jobExecution.getId(), e.getMessage());
        }
    }

    private void updateBatchJobStatus(String batchId, String status, JobCompletionStatistics stats) {
        try {
            logger.debug("Batch status updated: batchId={}, status={}", batchId, status);
        } catch (Exception e) {
            logger.warn("Batch status update failed: batchId={}, message={}", batchId, e.getMessage());
        }
    }

    private void generateCompletionReport(JobCompletionStatistics stats, boolean isSuccess) {
        try {
            logger.info("Completion report generated: batchId={}, status={}", stats.getBatchId(),
                    isSuccess ? "SUCCESS" : "FAILURE");
        } catch (Exception e) {
            logger.warn("Completion report generation failed: batchId={}, message={}", stats.getBatchId(), e.getMessage());
        }
    }

    private void logFailureAnalysis(JobExecution jobExecution) {
        for (StepExecution stepExecution : jobExecution.getStepExecutions()) {
            if (stepExecution.getStatus() == BatchStatus.FAILED) {
                logger.error("Step Failure: step={}, exitDescription={}", stepExecution.getStepName(),
                        stepExecution.getExitStatus().getExitDescription());
                stepExecution.getFailureExceptions().forEach(exception ->
                        logger.error("Step Exception: {}", exception.getMessage(), exception));
            }
        }
        jobExecution.getFailureExceptions().forEach(exception ->
                logger.error("Job Exception: {}", exception.getMessage(), exception));
    }

    private void logSystemInformation() {
        Runtime runtime = Runtime.getRuntime();
        logger.debug("System Info: processors={}, maxMem={}MB, totalMem={}MB, freeMem={}MB, usedMem={}MB",
                runtime.availableProcessors(),
                runtime.maxMemory() / 1024 / 1024,
                runtime.totalMemory() / 1024 / 1024,
                runtime.freeMemory() / 1024 / 1024,
                (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024);
    }

    private String getBatchId(JobExecution jobExecution) {
        return jobExecution.getJobParameters().getString("batchId",
                "batch-" + jobExecution.getId());
    }

    public static class JobCompletionStatistics {
        @Setter private String batchId;
        @Setter private long totalCVs = 0;
        @Setter private long shortlistedCount = 0;
        @Setter private long duplicateCount = 0;
        @Setter private long rejectedCount = 0;
        @Setter private long errorCount = 0;
        private long readCount = 0;
        private long writeCount = 0;
        private long skipCount = 0;
        private long processCount = 0;
        @Setter private double averageProcessingTimeMs = 0.0;
        @Setter private double throughputPerSecond = 0.0;

        public String getBatchId() { return batchId; }
        public long getTotalCVs() { return totalCVs; }
        public long getShortlistedCount() { return shortlistedCount; }
        public long getDuplicateCount() { return duplicateCount; }
        public long getRejectedCount() { return rejectedCount; }
        public long getErrorCount() { return errorCount; }
        public long getReadCount() { return readCount; }
        public void addReadCount(long readCount) { this.readCount += readCount; }
        public long getWriteCount() { return writeCount; }
        public void addWriteCount(long writeCount) { this.writeCount += writeCount; }
        public long getSkipCount() { return skipCount; }
        public void addSkipCount(long skipCount) { this.skipCount += skipCount; }
        public long getProcessCount() { return processCount; }
        public void addProcessCount(long processCount) { this.processCount += processCount; }
        public double getAverageProcessingTimeMs() { return averageProcessingTimeMs; }
        public double getThroughputPerSecond() { return throughputPerSecond; }

        public double getShortlistedPercentage() { return totalCVs > 0 ? (shortlistedCount * 100.0) / totalCVs : 0.0; }
        public double getDuplicatePercentage() { return totalCVs > 0 ? (duplicateCount * 100.0) / totalCVs : 0.0; }
        public double getRejectedPercentage() { return totalCVs > 0 ? (rejectedCount * 100.0) / totalCVs : 0.0; }
        public double getErrorPercentage() { return totalCVs > 0 ? (errorCount * 100.0) / totalCVs : 0.0; }
    }
}
