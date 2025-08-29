package com.raketman.shortlisting.service;

import com.raketman.shortlisting.entity.CV;
import com.raketman.shortlisting.exception.CVNotFoundException;
import com.raketman.shortlisting.exception.CVProcessingException;
import com.raketman.shortlisting.repository.CVRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.*;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
public class CVProcessingService {

    private static final Logger log = LoggerFactory.getLogger(CVProcessingService.class);

    @Autowired private JobLauncher jobLauncher;
    @Autowired private Job cvProcessingJob;
    @Autowired private CVRepository cvRepository;
    @Autowired private DocumentParserService documentParserService;
    @Autowired private KeywordMatchingService keywordMatchingService;
    @Autowired private DuplicateDetectionService duplicateDetectionService;
    @Autowired private FileOrganizationService fileOrganizationService;

    public Map<String, Object> processCVsFromDirectory(String inputDirectory, Integer batchSize, Boolean async) {
        validateInputDirectory(inputDirectory);
        String batchId = generateBatchId();

        try {
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

            if (Boolean.TRUE.equals(async)) {
                processAsync(jobParameters);
                result.put("status", "STARTED");
                result.put("message", "Batch processing started asynchronously");
            } else {
                JobExecution jobExecution = jobLauncher.run(cvProcessingJob, jobParameters);
                result.put("status", jobExecution.getStatus().toString());
                result.put("jobExecutionId", jobExecution.getId());
                result.put("endTime", LocalDateTime.now());

                if (jobExecution.getStatus() == BatchStatus.COMPLETED) {
                    result.put("message", "Batch processing completed successfully");
                    result.putAll(getJobExecutionSummary(jobExecution));
                } else {
                    result.put("message", "Batch processing failed or stopped");
                    result.put("exitDescription", jobExecution.getExitStatus().getExitDescription());
                }
            }

            log.info("Batch {} initiated for directory {}", batchId, inputDirectory);
            return result;

        } catch (Exception e) {
            throw new CVProcessingException("Failed to process directory: " + inputDirectory, e);
        }
    }

    @Async
    public CompletableFuture<JobExecution> processAsync(JobParameters jobParameters) {
        try {
            JobExecution jobExecution = jobLauncher.run(cvProcessingJob, jobParameters);
            return CompletableFuture.completedFuture(jobExecution);
        } catch (Exception e) {
            throw new CVProcessingException("Async batch processing failed", e);
        }
    }

    @Transactional
    public CV processSingleCV(File cvFile) {
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
            cv.setSkills(keywordMatchingService.extractSkills(content));

            CV duplicate = duplicateDetectionService.findDuplicate(cv);
            if (duplicate != null) {
                cv.setStatus(CV.CVStatus.DUPLICATE);
                cv.setDuplicateOf(duplicate.getId());
                cv.setSimilarityScore(duplicateDetectionService.calculateSimilarityScore(cv, duplicate));
            } else {
                boolean isShortlisted = keywordMatchingService.matchesCriteria(cv);
                cv.setStatus(isShortlisted ? CV.CVStatus.SHORTLISTED : CV.CVStatus.REJECTED);
            }

            cv.setProcessingTimeMs(System.currentTimeMillis() - startTime);
            CV savedCV = cvRepository.save(cv);
            fileOrganizationService.organizeFile(savedCV);

            log.info("CV {} processed with status {}", savedCV.getFileName(), savedCV.getStatus());
            return savedCV;

        } catch (Exception e) {
            throw new CVProcessingException("Failed to process CV file: " + cvFile.getName(), e);
        }
    }

    @Transactional(readOnly = true)
    public Page<CV> getShortlistedCVs(Pageable pageable, List<String> skills) {
        return (skills != null && !skills.isEmpty())
                ? cvRepository.findShortlistedWithSkills(skills, pageable)
                : cvRepository.findByStatus(CV.CVStatus.SHORTLISTED, pageable);
    }

    @Transactional(readOnly = true)
    public Page<CV> getDuplicateCVs(Pageable pageable) {
        return cvRepository.findByStatus(CV.CVStatus.DUPLICATE, pageable);
    }

    @Transactional(readOnly = true)
    public Page<CV> searchCVs(String query, CV.CVStatus status, Pageable pageable) {
        return cvRepository.searchCVs(query, status, pageable);
    }

    @Transactional(readOnly = true)
    public CV getCVById(Long id) {
        return cvRepository.findById(id)
                .orElseThrow(() -> new CVNotFoundException("CV not found with ID: " + id));
    }

    @Transactional
    public boolean deleteCV(Long id) {
        if (!cvRepository.existsById(id)) {
            throw new CVNotFoundException("CV not found with ID: " + id);
        }
        cvRepository.deleteById(id);
        log.info("Deleted CV with ID {}", id);
        return true;
    }

    @Transactional
    public CV updateCVStatus(Long id, CV.CVStatus newStatus) {
        CV cv = cvRepository.findById(id)
                .orElseThrow(() -> new CVNotFoundException("CV not found with ID: " + id));

        CV.CVStatus oldStatus = cv.getStatus();
        cv.setStatus(newStatus);
        cv.setLastModifiedDate(LocalDateTime.now());

        CV updatedCV = cvRepository.save(cv);
        log.info("Updated CV {} status from {} to {}", id, oldStatus, newStatus);
        return updatedCV;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getBatchStatistics(String batchId) {
        List<CV> batchCVs = cvRepository.findByBatchId(batchId);
        Map<String, Object> stats = new HashMap<>();
        stats.put("batchId", batchId);
        stats.put("totalCVs", batchCVs.size());

        if (!batchCVs.isEmpty()) {
            Map<CV.CVStatus, Long> statusCounts = batchCVs.stream()
                    .collect(Collectors.groupingBy(CV::getStatus, Collectors.counting()));
            stats.put("shortlisted", statusCounts.getOrDefault(CV.CVStatus.SHORTLISTED, 0L));
            stats.put("duplicates", statusCounts.getOrDefault(CV.CVStatus.DUPLICATE, 0L));
            stats.put("rejected", statusCounts.getOrDefault(CV.CVStatus.REJECTED, 0L));
            stats.put("errors", statusCounts.getOrDefault(CV.CVStatus.ERROR, 0L));

            batchCVs.stream().map(CV::getCreatedDate).filter(Objects::nonNull)
                    .min(LocalDateTime::compareTo).ifPresent(d -> stats.put("startTime", d));
            batchCVs.stream().map(CV::getLastModifiedDate).filter(Objects::nonNull)
                    .max(LocalDateTime::compareTo).ifPresent(d -> stats.put("endTime", d));

            batchCVs.stream().filter(cv -> cv.getProcessingTimeMs() != null)
                    .mapToLong(CV::getProcessingTimeMs).average()
                    .ifPresent(avg -> stats.put("averageProcessingTimeMs", avg));
        }

        return stats;
    }

    @Transactional
    public Map<String, Object> reprocessDuplicates() {
        duplicateDetectionService.reprocessDuplicatesInBatch();
        long totalDuplicates = cvRepository.countByStatus(CV.CVStatus.DUPLICATE);

        Map<String, Object> result = new HashMap<>();
        result.put("status", "COMPLETED");
        result.put("message", "Duplicate reprocessing completed");
        result.put("processedAt", LocalDateTime.now());
        result.put("totalDuplicatesFound", totalDuplicates);

        log.info("Reprocessed duplicates. Total found: {}", totalDuplicates);
        return result;
    }

    private void validateInputDirectory(String inputDirectory) {
        File directory = new File(Optional.ofNullable(inputDirectory)
                .orElseThrow(() -> new IllegalArgumentException("Input directory cannot be null")));

        if (!directory.exists() || !directory.isDirectory() || !directory.canRead()) {
            throw new IllegalArgumentException("Invalid directory: " + inputDirectory);
        }
    }

    private String generateBatchId() {
        return "batch-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss"))
                + "-" + String.format("%03d", new Random().nextInt(1000));
    }

    private String getFileExtension(String fileName) {
        int lastDot = fileName.lastIndexOf('.');
        return lastDot > 0 ? fileName.substring(lastDot + 1).toLowerCase() : "";
    }

    private Map<String, Object> getJobExecutionSummary(JobExecution jobExecution) {
        Map<String, Object> summary = new HashMap<>();
        for (StepExecution stepExecution : jobExecution.getStepExecutions()) {
            summary.put("readCount", stepExecution.getReadCount());
            summary.put("writeCount", stepExecution.getWriteCount());
            summary.put("skipCount", stepExecution.getSkipCount());
            summary.put("commitCount", stepExecution.getCommitCount());
        }
        return summary;
    }
}
