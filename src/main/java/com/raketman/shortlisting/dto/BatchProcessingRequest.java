package com.raketman.shortlisting.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.*;
import lombok.*;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BatchProcessingRequest {

    @NotBlank(message = "Input directory is required")
    private String inputDirectory;

    @Min(value = 1, message = "Batch size must be at least 1")
    @Max(value = 100, message = "Batch size cannot exceed 100")
    @Builder.Default
    private Integer batchSize = 10;

    @Builder.Default
    private Boolean async = true;

    @Min(value = 1, message = "Concurrent threads must be at least 1")
    @Max(value = 50, message = "Concurrent threads cannot exceed 50")
    @Builder.Default
    private Integer maxConcurrentThreads = 20;

    @Pattern(regexp = "^[a-zA-Z0-9_-]+$",
            message = "Batch ID can only contain letters, numbers, underscores, and hyphens")
    private String customBatchId;

    private List<String> includeFormats;
    private List<String> excludePatterns;
    private KeywordMatchingConfig keywordConfig;

    @Min(value = 1, message = "Priority must be between 1 and 10")
    @Max(value = 10, message = "Priority must be between 1 and 10")
    @Builder.Default
    private Integer priority = 5;

    @Builder.Default
    private Boolean skipDuplicateDetection = false;

    @Builder.Default
    private Boolean organizeFiles = true;

    @Builder.Default
    private Boolean enableNotifications = false;

    @Email(message = "Invalid email format")
    private String notificationEmail;

    @Min(value = 1000, message = "Timeout must be at least 1000ms")
    @Max(value = 300000, message = "Timeout cannot exceed 300000ms (5 minutes)")
    @Builder.Default
    private Long processingTimeoutMs = 30000L;

    private Map<String, String> customParameters;

    @Builder.Default
    private Boolean continueOnError = true;

    @Min(value = 1, message = "Error limit must be at least 1")
    @Max(value = 1000, message = "Error limit cannot exceed 1000")
    @Builder.Default
    private Integer maxErrorCount = 50;


    public Boolean isAsync() {
        return async != null && async;
    }


    public boolean shouldSkipDuplicateDetection() {
        return Boolean.TRUE.equals(skipDuplicateDetection);
    }

    public boolean shouldOrganizeFiles() {
        return organizeFiles == null || organizeFiles;
    }

    public boolean shouldSendNotifications() {
        return Boolean.TRUE.equals(enableNotifications);
    }

    public boolean shouldContinueOnError() {
        return continueOnError == null || continueOnError;
    }

    public String generateBatchId() {
        if (customBatchId != null && !customBatchId.trim().isEmpty()) {
            return customBatchId.trim();
        }
        return "batch-" + System.currentTimeMillis();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class KeywordMatchingConfig {

        private String requiredKeywords;
        private String optionalKeywords;
        private String excludedKeywords;

        @Pattern(regexp = "^(AND|OR|WEIGHTED)$",
                message = "Matching mode must be AND, OR, or WEIGHTED")
        private String matchingMode;

        @Min(value = 0, message = "Threshold must be between 0 and 100")
        @Max(value = 100, message = "Threshold must be between 0 and 100")
        private Integer matchingThreshold;
    }
}
