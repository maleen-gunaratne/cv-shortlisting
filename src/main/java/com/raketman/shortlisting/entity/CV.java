package com.raketman.shortlisting.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "cvs", indexes = {
        @Index(name = "idx_cv_email", columnList = "email"),
        @Index(name = "idx_cv_phone", columnList = "phoneNumber"),
        @Index(name = "idx_cv_name", columnList = "fullName"),
        @Index(name = "idx_cv_status", columnList = "status"),
        @Index(name = "idx_cv_created_date", columnList = "createdDate")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class CV {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Full name is required")
    @Size(max = 255)
    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Email(message = "Invalid email format")
    @Size(max = 255)
    @Column(name = "email", unique = false)
    private String email;

    @Size(max = 20)
    @Column(name = "phone_number")
    private String phoneNumber;

    @NotBlank(message = "File name is required")
    @Size(max = 500)
    @Column(name = "file_name", nullable = false)
    private String fileName;

    @NotBlank(message = "File path is required")
    @Size(max = 1000)
    @Column(name = "file_path", nullable = false)
    private String filePath;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "file_type", length = 10)
    private String fileType;

    @Lob
    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private CVStatus status = CVStatus.PENDING;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "cv_skills", joinColumns = @JoinColumn(name = "cv_id"))
    @Column(name = "skill")
    private Set<String> skills = new HashSet<>();

    @Column(name = "duplicate_of")
    private Long duplicateOf;

    @Column(name = "similarity_score")
    private Double similarityScore;

    @Column(name = "processing_time_ms")
    private Long processingTimeMs;

    @Column(name = "batch_id")
    private String batchId;

    @CreatedDate
    @Column(name = "created_date", nullable = false, updatable = false)
    private LocalDateTime createdDate;

    @LastModifiedDate
    @Column(name = "last_modified_date")
    private LocalDateTime lastModifiedDate;

    @Column(name = "processed_by")
    private String processedBy;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    public enum CVStatus {
        PENDING,        // Just uploaded, not processed yet
        PROCESSING,     // Currently being processed
        SHORTLISTED,    // Matches required keywords
        DUPLICATE,      // Identified as duplicate
        REJECTED,       // Doesn't match criteria
        ERROR           // Error during processing
    }

}
