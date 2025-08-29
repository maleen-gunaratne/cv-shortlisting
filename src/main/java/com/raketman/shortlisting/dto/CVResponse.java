package com.raketman.shortlisting.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.raketman.shortlisting.entity.CV;
import lombok.*;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;


@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CVResponse {

    private Long id;
    private String fullName;
    private String email;
    private String phoneNumber;
    private String fileName;
    private String fileType;
    private Long fileSize;
    private CV.CVStatus status;
    private Set<String> skills;
    private Long duplicateOf;
    private Double similarityScore;
    private Long processingTimeMs;
    private String batchId;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdDate;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime lastModifiedDate;

    private String processedBy;
    private String errorMessage;
    private String contentPreview;

    public boolean isDuplicate() {
        return duplicateOf != null;
    }

    public boolean hasError() {
        return status == CV.CVStatus.ERROR;
    }

    public boolean isShortlisted() {
        return status == CV.CVStatus.SHORTLISTED;
    }

    public String getProcessingTimeFormatted() {
        return Optional.ofNullable(processingTimeMs)
                .map(ms -> ms < 1000 ? ms + " ms" : String.format("%.2f s", ms / 1000.0))
                .orElse("Unknown");
    }

    public int getSkillCount() {
        return skills != null ? skills.size() : 0;
    }

    /**
     * Static factory method to create CVResponse from CV entity
     */
    public static CVResponse fromEntity(CV cv) {
        if (cv == null) return null;

        CVResponse response = new CVResponse();
        response.setId(cv.getId());
        response.setFullName(cv.getFullName());
        response.setEmail(cv.getEmail());
        response.setPhoneNumber(cv.getPhoneNumber());
        response.setFileName(cv.getFileName());
        response.setFileType(cv.getFileType());
        response.setFileSize(cv.getFileSize());
        response.setStatus(cv.getStatus());
        response.setSkills(cv.getSkills());
        response.setDuplicateOf(cv.getDuplicateOf());
        response.setSimilarityScore(cv.getSimilarityScore());
        response.setProcessingTimeMs(cv.getProcessingTimeMs());
        response.setBatchId(cv.getBatchId());
        response.setCreatedDate(cv.getCreatedDate());
        response.setLastModifiedDate(cv.getLastModifiedDate());
        response.setProcessedBy(cv.getProcessedBy());
        response.setErrorMessage(cv.getErrorMessage());

        // Set content preview (truncated for API responses)
        if (cv.getContent() != null) {
            if (cv.getContent().length() > 500) {
                response.setContentPreview(cv.getContent().substring(0, 500) + "...");
            } else {
                response.setContentPreview(cv.getContent());
            }
        }

        return response;
    }


    public static CVResponse createMinimal(CV cv) {
        if (cv == null) return null;

        CVResponse response = new CVResponse();
        response.setId(cv.getId());
        response.setFullName(cv.getFullName());
        response.setEmail(cv.getEmail());
        response.setFileName(cv.getFileName());
        response.setStatus(cv.getStatus());
        response.setCreatedDate(cv.getCreatedDate());
        response.setSkills(cv.getSkills());
        response.setDuplicateOf(cv.getDuplicateOf());

        return response;
    }
}
