package com.raketman.shortlisting.service;

import com.raketman.shortlisting.dto.ProcessingStatsResponse;
import com.raketman.shortlisting.entity.CV;
import com.raketman.shortlisting.repository.CVRepository;
import org.springframework.stereotype.Service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.*;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.repository.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.repository.JobRestartException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Main service for orchestrating CV processing operations
 * Coordinates between batch processing, individual services, and data management
 */
@Service
public class CVProcessingService {

    private static final Logger logger = LoggerFactory.getLogger(CVProcessingService.class);

    @Autowired
    private JobLauncher jobLauncher;

    @Autowired
    private Job cvProcessingJob;

    @Autowired
    private CVRepository cvRepository;

    @Autowired
    private DocumentParserService documentParserService;

    @Autowired
    private KeywordMatchingService keywordMatchingService;

    @Autowired
    private DuplicateDetectionService duplicateDetectionService;

    @Autowired
    private FileOrganizationService fileOrganizationService;


    /**
     * Process CVs from a directory using Spring Batch
     */
    public Map<String, Object> processCVsFromDirectory(String inputDirectory, Integer batchSize, Boolean async) {
        logger.info("Starting CV processing from directory: {}", inputDirectory);

        try {
            validateInputDirectory(inputDirectory);
            String batchId = generateBatchId();

            // Prepare job parameters
            JobParameters jobParameters = new JobParametersBuilder()
                    .addString("inputDir", inputDirectory)
                    .addString("batchId", batchId)
                    .addLong("timestamp", System.currentTimeMillis())
                    .addLong("batchSize", batchSize != null ? batchSize.longValue() : 10L)
                    .toJobParameters();

            Map<String, Object> result = new HashMap<>();
            result.put("batchId", batchId);
            result.put("inputDirectory", inputDirectory);
            result.put("batchSize", batchSize);
            result.put("async", async);
            result.put("startTime", LocalDateTime.now());

            if (async != null && async) {
                // Process asynchronously
                CompletableFuture<JobExecution> futureExecution = processAsync(jobParameters);   //async process
                result.put("status", "STARTED");
                result.put("message", "Batch processing started asynchronously");
                result.put("jobExecutionId", "pending");
            } else {
                // Process synchronously
                JobExecution jobExecution = jobLauncher.run(cvProcessingJob, jobParameters);
                result.put("status", jobExecution.getStatus().toString());
                result.put("jobExecutionId", jobExecution.getId());
                result.put("endTime", LocalDateTime.now());

                if (jobExecution.getStatus() == BatchStatus.COMPLETED) {
                    result.put("message", "Batch processing completed successfully");
                    result.putAll(getJobExecutionSummary(jobExecution));
                } else {
                    result.put("message", "Batch processing failed or was stopped");
                    result.put("exitDescription", jobExecution.getExitStatus().getExitDescription());
                }
            }

            logger.info("CV processing initiated for batch: {}", batchId);
            return result;

        } catch (Exception e) {
            logger.error("Error starting CV processing from directory: {}", inputDirectory, e);
            throw new CVProcessingException("Failed to start CV processing: " + e.getMessage(), e);
        }
    }

    /**
     * Process CVs asynchronously
     */
    @Async
    public CompletableFuture<JobExecution> processAsync(JobParameters jobParameters) {
        try {
            JobExecution jobExecution = jobLauncher.run(cvProcessingJob, jobParameters);
            return CompletableFuture.completedFuture(jobExecution);
        } catch (Exception e) {
            logger.error("Error in async CV processing", e);
            throw new CVProcessingException("Async processing failed: " + e.getMessage(), e);
        }
    }

    /**
     * Process a single CV file
     */
    @Transactional
    public CV processSingleCV(File cvFile) throws CVProcessingException {
        logger.info("Processing single CV file: {}", cvFile.getName());
        long startTime = System.currentTimeMillis();

        try {

            CV cv = new CV();
            cv.setFileName(cvFile.getName());
            cv.setFilePath(cvFile.getAbsolutePath());
            cv.setFileSize(cvFile.length());
            cv.setFileType(getFileExtension(cvFile.getName()));
            cv.setStatus(CV.CVStatus.PROCESSING);
            cv.setBatchId("single-" + System.currentTimeMillis());
            cv.setProcessedBy(Thread.currentThread().getName());

            String content = documentParserService.parseDocument(cvFile);
            cv.setContent(content);

            cv.setEmail(documentParserService.extractEmail(content));
            cv.setPhoneNumber(documentParserService.extractPhoneNumber(content));
            cv.setFullName(documentParserService.extractName(content));

            Set<String> skills = keywordMatchingService.extractSkills(content);
            cv.setSkills(skills);

            CV duplicateCV = duplicateDetectionService.findDuplicate(cv);
            if (duplicateCV != null) {
                cv.setStatus(CV.CVStatus.DUPLICATE);
                cv.setDuplicateOf(duplicateCV.getId());
                cv.setSimilarityScore(duplicateDetectionService.calculateSimilarityScore(cv, duplicateCV));
            } else {
                boolean isShortlisted = keywordMatchingService.matchesCriteria(cv);
                cv.setStatus(isShortlisted ? CV.CVStatus.SHORTLISTED : CV.CVStatus.REJECTED);
            }

            cv.setProcessingTimeMs(System.currentTimeMillis() - startTime);

            cv = cvRepository.save(cv);

            fileOrganizationService.organizeFile(cv);

            logger.info("Successfully processed CV: {} (Status: {})", cv.getFileName(), cv.getStatus());
            return cv;

        } catch (Exception e) {
            logger.error("Error processing CV file: {}", cvFile.getName(), e);
            throw new CVProcessingException("Failed to process CV: " + e.getMessage(), e);
        }
    }

    /**
     * Get shortlisted CVs with optional skill filtering
     */
    @Transactional(readOnly = true)
    public Page<CV> getShortlistedCVs(Pageable pageable, List<String> skills) {
        if (skills != null && !skills.isEmpty()) {
            return cvRepository.findShortlistedWithSkills(skills, pageable);
        } else {
            return cvRepository.findByStatus(CV.CVStatus.SHORTLISTED, pageable);
        }
    }

    /**
     * Get duplicate CVs
     */
    @Transactional(readOnly = true)
    public Page<CV> getDuplicateCVs(Pageable pageable) {
        return cvRepository.findByStatus(CV.CVStatus.DUPLICATE, pageable);
    }

    /**
     * Search CVs with full-text search
     */
    @Transactional(readOnly = true)
    public Page<CV> searchCVs(String query, CV.CVStatus status, Pageable pageable) {
        return cvRepository.searchCVs(query, status, pageable);
    }

    /**
     * Get CV by ID
     */
    @Transactional(readOnly = true)
    public CV getCVById(Long id) {
        return cvRepository.findById(id).orElse(null);
    }

    /**
     * Delete CV by ID
     */
    @Transactional
    public boolean deleteCV(Long id) {
        try {
            if (cvRepository.existsById(id)) {
                cvRepository.deleteById(id);
                logger.info("Deleted CV with ID: {}", id);
                return true;
            }
            return false;
        } catch (Exception e) {
            logger.error("Error deleting CV with ID: {}", id, e);
            throw new CVProcessingException("Failed to delete CV: " + e.getMessage(), e);
        }
    }

    /**
     * Update CV status
     */
    @Transactional
    public CV updateCVStatus(Long id, CV.CVStatus newStatus) {
        try {
            Optional<CV> cvOptional = cvRepository.findById(id);
            if (cvOptional.isPresent()) {
                CV cv = cvOptional.get();
                CV.CVStatus oldStatus = cv.getStatus();
                cv.setStatus(newStatus);
                cv.setLastModifiedDate(LocalDateTime.now());
                cv = cvRepository.save(cv);

                logger.info("Updated CV {} status from {} to {}", id, oldStatus, newStatus);
                return cv;
            }
            return null;
        } catch (Exception e) {
            logger.error("Error updating CV status for ID: {}", id, e);
            throw new CVProcessingException("Failed to update CV status: " + e.getMessage(), e);
        }
    }

    /**
     * Get comprehensive processing statistics
     */
//    @Transactional(readOnly = true)
//    public ProcessingStatsResponse getProcessingStatistics() {
//        logger.debug("Generating processing statistics");
//
//        ProcessingStatsResponse stats = new ProcessingStatsResponse();
//
//        try {
//            // Overall statistics
//            stats.setOverallStats(buildOverallStats());
//
//            // Performance metrics
//            stats.setPerformanceMetrics(buildPerformanceMetrics());
//
//            // Recent batch statistics
//            stats.setBatchStats(buildRecentBatchStats());
//
//            // Daily statistics (last 30 days)
//            stats.setDailyStats(buildDailyStats());
//
//            // Top skills
//            stats.setTopSkills(buildTopSkillsStats());
//
//            // File type statistics
//            stats.setFileTypeStats(buildFileTypeStats());
//
//            // Error analysis
//            stats.setErrorAnalysis(buildErrorAnalysis());
//
//            // System statistics
//            stats.setSystemStats(buildSystemStats());
//
//            logger.debug("Generated comprehensive processing statistics");
//            return stats;
//
//        } catch (Exception e) {
//            logger.error("Error generating processing statistics", e);
//            throw new CVProcessingException("Failed to generate statistics: " + e.getMessage(), e);
//        }
//    }

    /**
     * Get processing statistics for a specific batch
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getBatchStatistics(String batchId) {
        List<CV> batchCVs = cvRepository.findByBatchId(batchId);

        Map<String, Object> stats = new HashMap<>();
        stats.put("batchId", batchId);
        stats.put("totalCVs", batchCVs.size());

        if (!batchCVs.isEmpty()) {
            // Status breakdown
            Map<CV.CVStatus, Long> statusCounts = batchCVs.stream()
                    .collect(Collectors.groupingBy(CV::getStatus, Collectors.counting()));

            stats.put("shortlisted", statusCounts.getOrDefault(CV.CVStatus.SHORTLISTED, 0L));
            stats.put("duplicates", statusCounts.getOrDefault(CV.CVStatus.DUPLICATE, 0L));
            stats.put("rejected", statusCounts.getOrDefault(CV.CVStatus.REJECTED, 0L));
            stats.put("errors", statusCounts.getOrDefault(CV.CVStatus.ERROR, 0L));

            // Processing times
            OptionalDouble avgProcessingTime = batchCVs.stream()
                    .filter(cv -> cv.getProcessingTimeMs() != null)
                    .mapToLong(CV::getProcessingTimeMs)
                    .average();

            if (avgProcessingTime.isPresent()) {
                stats.put("averageProcessingTimeMs", avgProcessingTime.getAsDouble());
            }

            // Batch timing
            Optional<LocalDateTime> earliestDate = batchCVs.stream()
                    .map(CV::getCreatedDate)
                    .filter(Objects::nonNull)
                    .min(LocalDateTime::compareTo);

            Optional<LocalDateTime> latestDate = batchCVs.stream()
                    .map(CV::getLastModifiedDate)
                    .filter(Objects::nonNull)
                    .max(LocalDateTime::compareTo);

            earliestDate.ifPresent(date -> stats.put("startTime", date));
            latestDate.ifPresent(date -> stats.put("endTime", date));
        }

        return stats;
    }

    /**
     * Reprocess CVs for duplicate detection
     */
    @Transactional
    public Map<String, Object> reprocessDuplicates() {
        logger.info("Starting duplicate reprocessing for all CVs");

        try {
            duplicateDetectionService.reprocessDuplicatesInBatch();

            Map<String, Object> result = new HashMap<>();
            result.put("status", "COMPLETED");
            result.put("message", "Duplicate reprocessing completed successfully");
            result.put("processedAt", LocalDateTime.now());

            // Get updated statistics
            long totalDuplicates = cvRepository.countByStatus(CV.CVStatus.DUPLICATE);
            result.put("totalDuplicatesFound", totalDuplicates);

            return result;

        } catch (Exception e) {
            logger.error("Error during duplicate reprocessing", e);
            throw new CVProcessingException("Duplicate reprocessing failed: " + e.getMessage(), e);
        }
    }

    // Private helper methods

    private void validateInputDirectory(String inputDirectory) {
        if (inputDirectory == null || inputDirectory.trim().isEmpty()) {
            throw new IllegalArgumentException("Input directory cannot be null or empty");
        }

        File directory = new File(inputDirectory);
        if (!directory.exists()) {
            throw new IllegalArgumentException("Input directory does not exist: " + inputDirectory);
        }

        if (!directory.isDirectory()) {
            throw new IllegalArgumentException("Path is not a directory: " + inputDirectory);
        }

        if (!directory.canRead()) {
            throw new IllegalArgumentException("Cannot read from directory: " + inputDirectory);
        }
    }

    private String generateBatchId() {
        return "batch-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss")) +
                "-" + String.format("%03d", new Random().nextInt(1000));
    }

    private String getFileExtension(String fileName) {
        int lastDotIndex = fileName.lastIndexOf('.');
        return lastDotIndex > 0 ? fileName.substring(lastDotIndex + 1).toLowerCase() : "";
    }

    private Map<String, Object> getJobExecutionSummary(JobExecution jobExecution) {
        Map<String, Object> summary = new HashMap<>();

        Collection<StepExecution> stepExecutions = jobExecution.getStepExecutions();
        for (StepExecution stepExecution : stepExecutions) {
            summary.put("readCount", stepExecution.getReadCount());
            summary.put("writeCount", stepExecution.getWriteCount());
            summary.put("skipCount", stepExecution.getSkipCount());
            summary.put("commitCount", stepExecution.getCommitCount());
        }

        return summary;
    }

    @Transactional(readOnly = true)
    private ProcessingStatsResponse.OverallStats buildOverallStats() {
        ProcessingStatsResponse.OverallStats stats = new ProcessingStatsResponse.OverallStats();

        stats.setTotalCVs(cvRepository.count());
        stats.setShortlistedCVs(cvRepository.countByStatus(CV.CVStatus.SHORTLISTED));
        stats.setDuplicateCVs(cvRepository.countByStatus(CV.CVStatus.DUPLICATE));
        stats.setRejectedCVs(cvRepository.countByStatus(CV.CVStatus.REJECTED));
        stats.setErrorCVs(cvRepository.countByStatus(CV.CVStatus.ERROR));

        // Calculate rates
        if (stats.getTotalCVs() > 0) {
            stats.setShortlistRate((stats.getShortlistedCVs() * 100.0) / stats.getTotalCVs());
            stats.setDuplicateRate((stats.getDuplicateCVs() * 100.0) / stats.getTotalCVs());
            stats.setErrorRate((stats.getErrorCVs() * 100.0) / stats.getTotalCVs());
        }

        // Count unique skills
        List<Object[]> skillStats = cvRepository.getTopSkills(1000);
        stats.setTotalUniqueSkills((long) skillStats.size());

        return stats;
    }

//    @Transactional(readOnly = true)
//    private ProcessingStatsResponse.PerformanceMetrics buildPerformanceMetrics() {
//        ProcessingStatsResponse.PerformanceMetrics metrics = new ProcessingStatsResponse.PerformanceMetrics();
//
//        // Get processing time metrics
//        List<Object[]> timeMetrics = cvRepository.getProcessingTimeMetrics();
//        if (!timeMetrics.isEmpty()) {
//            Object[] row = timeMetrics.get(0);
//            if (row[0] != null) metrics.setAverageProcessingTimeMs(((Number) row[0]).doubleValue());
//            if (row[1] != null) metrics.setMaxProcessingTimeMs(((Number) row[1]).longValue());
//            if (row[2] != null) metrics.setMinProcessingTimeMs(((Number) row[2]).longValue());
//        }
//
//        // Get current performance metrics from monitoring service
//        PerformanceMonitoringService.PerformanceStatistics currentStats =
//                performanceMonitoringService.getCurrentStatistics();
//
//        metrics.setCurrentThroughput((double) currentStats.getCurrentThroughput());
//        metrics.setMemoryUsageMB((long) (currentStats.getCurrentMemoryUsage() / (1024 * 1024)));
//
//        // System information
//        Runtime runtime = Runtime.getRuntime();
//        metrics.setConcurrentThreads(runtime.availableProcessors());
//
//        return metrics;
//    }

    @Transactional(readOnly = true)
    private List<ProcessingStatsResponse.BatchStats> buildRecentBatchStats() {
        // Get recent batch performance metrics (last 10 batches)
        List<Object[]> batchMetrics = cvRepository.getBatchPerformanceMetrics();

        return batchMetrics.stream()
                .limit(10)
                .map(row -> {
                    ProcessingStatsResponse.BatchStats batchStats = new ProcessingStatsResponse.BatchStats();
                    batchStats.setBatchId((String) row[0]);
                    batchStats.setTotalCVs(((Number) row[1]).intValue());
                    if (row[2] != null) {
                        batchStats.setDurationSeconds(((Number) row[2]).longValue());
                    }
                    batchStats.setStatus("COMPLETED");
                    return batchStats;
                })
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    private List<ProcessingStatsResponse.DailyStats> buildDailyStats() {
        LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);
        List<Object[]> dailyMetrics = cvRepository.getDailyProcessingStats(thirtyDaysAgo);

        return dailyMetrics.stream()
                .map(row -> {
                    ProcessingStatsResponse.DailyStats dailyStats = new ProcessingStatsResponse.DailyStats();
                    dailyStats.setDate(row[0].toString());
                    dailyStats.setTotalCVs(((Number) row[1]).intValue());
                    return dailyStats;
                })
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    private List<ProcessingStatsResponse.SkillStats> buildTopSkillsStats() {
        List<Object[]> topSkills = cvRepository.getTopSkills(20);
        long totalCVs = cvRepository.count();

        return topSkills.stream()
                .map(row -> {
                    ProcessingStatsResponse.SkillStats skillStats = new ProcessingStatsResponse.SkillStats();
                    skillStats.setSkill((String) row[0]);
                    skillStats.setCount(((Number) row[1]).intValue());
                    if (totalCVs > 0) {
                        skillStats.setPercentage((skillStats.getCount() * 100.0) / totalCVs);
                    }
                    return skillStats;
                })
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    private List<ProcessingStatsResponse.FileTypeStats> buildFileTypeStats() {
        List<Object[]> fileTypeMetrics = cvRepository.getFileTypeStatistics();

        return fileTypeMetrics.stream()
                .map(row -> {
                    ProcessingStatsResponse.FileTypeStats fileTypeStats = new ProcessingStatsResponse.FileTypeStats();
                    fileTypeStats.setFileType((String) row[0]);
                    fileTypeStats.setCount(((Number) row[1]).intValue());
                    if (row[2] != null) {
                        fileTypeStats.setAverageProcessingTime(((Number) row[2]).doubleValue());
                    }
                    return fileTypeStats;
                })
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    private ProcessingStatsResponse.ErrorAnalysis buildErrorAnalysis() {
        ProcessingStatsResponse.ErrorAnalysis errorAnalysis = new ProcessingStatsResponse.ErrorAnalysis();

        List<CV> errorCVs = cvRepository.findByStatusAndErrorMessageIsNotNull(CV.CVStatus.ERROR);
        errorAnalysis.setTotalErrors(errorCVs.size());

        // Get error type distribution
        List<Object[]> errorStats = cvRepository.getErrorStatistics();
        Map<String, Integer> errorTypes = errorStats.stream()
                .collect(Collectors.toMap(
                        row -> (String) row[0],
                        row -> ((Number) row[1]).intValue()
                ));
        errorAnalysis.setErrorTypes(errorTypes);

        return errorAnalysis;
    }

//    private ProcessingStatsResponse.SystemStats buildSystemStats() {
//        ProcessingStatsResponse.SystemStats systemStats = new ProcessingStatsResponse.SystemStats();
//
//        Runtime runtime = Runtime.getRuntime();
//        long usedMemory = runtime.totalMemory() - runtime.freeMemory();
//
//        systemStats.setCurrentMemoryUsageMB(usedMemory / (1024 * 1024));
//        systemStats.setMaxMemoryMB(runtime.maxMemory() / (1024 * 1024));
//        systemStats.setMemoryUsagePercent((usedMemory * 100.0) / runtime.maxMemory());
//        systemStats.setAvailableCores(runtime.availableProcessors());
//
//        // Get active jobs from performance monitoring
//        PerformanceMonitoringService.PerformanceStatistics perfStats =
//                performanceMonitoringService.getCurrentStatistics();
//        systemStats.setActiveJobs((int) perfStats.getActiveJobs());
//
//        return systemStats;
//    }

    /**
     * Custom exception for CV processing errors
     */
    public static class CVProcessingException extends RuntimeException {
        public CVProcessingException(String message) {
            super(message);
        }

        public CVProcessingException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
