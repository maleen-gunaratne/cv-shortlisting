package com.raketman.shortlisting.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProcessingStatsResponse {

    private OverallStats overallStats;
    private PerformanceMetrics performanceMetrics;
    private List<BatchStats> batchStats;
    private List<DailyStats> dailyStats;
    private List<SkillStats> topSkills;
    private List<FileTypeStats> fileTypeStats;
    private ErrorAnalysis errorAnalysis;
    private SystemStats systemStats;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime generatedAt = LocalDateTime.now();

    @Getter
    @Setter
    @NoArgsConstructor
    public static class OverallStats {
        private Long totalCVs;
        private Long shortlistedCVs;
        private Long duplicateCVs;
        private Long rejectedCVs;
        private Long errorCVs;
        private Double shortlistRate;
        private Double duplicateRate;
        private Double errorRate;
        private Long totalUniqueSkills;
        private Long totalBatchJobs;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class PerformanceMetrics {
        private Double averageProcessingTimeMs;
        private Long minProcessingTimeMs;
        private Long maxProcessingTimeMs;
        private Double currentThroughput;
        private Double peakThroughput;
        private Double averageBatchTimeSeconds;
        private Long memoryUsageMB;
        private Double cpuUsagePercent;
        private Integer concurrentThreads;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class BatchStats {
        private String batchId;
        private Integer totalCVs;

        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
        private LocalDateTime startTime;

        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
        private LocalDateTime endTime;

        private Long durationSeconds;
        private String status;
        private Integer successfulCVs;
        private Integer failedCVs;
        private Double throughput;

        public BatchStats(String batchId, Integer totalCVs, String status) {
            this.batchId = batchId;
            this.totalCVs = totalCVs;
            this.status = status;
        }
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class DailyStats {
        private String date;
        private Integer totalCVs;
        private Integer shortlisted;
        private Integer duplicates;
        private Integer rejected;
        private Integer errors;
        private Double averageProcessingTime;

        public DailyStats(String date, Integer totalCVs) {
            this.date = date;
            this.totalCVs = totalCVs;
        }
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class SkillStats {
        private String skill;
        private Integer count;
        private Double percentage;
        private Integer shortlistedCount;

        public SkillStats(String skill, Integer count, Double percentage) {
            this.skill = skill;
            this.count = count;
            this.percentage = percentage;
        }
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class FileTypeStats {
        private String fileType;
        private Integer count;
        private Double percentage;
        private Double averageProcessingTime;
        private Double successRate;

        public FileTypeStats(String fileType, Integer count, Double percentage) {
            this.fileType = fileType;
            this.count = count;
            this.percentage = percentage;
        }
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class ErrorAnalysis {
        private Integer totalErrors;
        private Map<String, Integer> errorTypes;
        private List<Double> errorRateTrend;
        private List<String> problematicFileTypes;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class SystemStats {
        private Long currentMemoryUsageMB;
        private Long maxMemoryMB;
        private Double memoryUsagePercent;
        private Integer activeJobs;
        private Integer availableCores;
        private Double systemUptimeHours;
        private Long diskUsageMB;
    }

    @Override
    public String toString() {
        return "ProcessingStatsResponse{" +
                "generatedAt=" + generatedAt +
                ", totalCVs=" + (overallStats != null ? overallStats.getTotalCVs() : "null") +
                ", shortlistedCVs=" + (overallStats != null ? overallStats.getShortlistedCVs() : "null") +
                ", batchCount=" + (batchStats != null ? batchStats.size() : 0) +
                '}';
    }
}
