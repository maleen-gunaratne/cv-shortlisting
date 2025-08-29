package com.raketman.shortlisting.repository;


import com.raketman.shortlisting.entity.CV;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface CVRepository  extends JpaRepository<CV, Long> {

    // Basic status-based queries
    List<CV> findByStatus(CV.CVStatus status);
    Page<CV> findByStatus(CV.CVStatus status, Pageable pageable);
    long countByStatus(CV.CVStatus status);

    // Email-based queries
    List<CV> findByEmailIgnoreCase(String email);
    Optional<CV> findFirstByEmailIgnoreCaseAndIdNot(String email, Long id);

    // Phone number queries
    @Query("SELECT c FROM CV c WHERE REPLACE(REPLACE(REPLACE(c.phoneNumber, ' ', ''), '-', ''), '+', '') = :normalizedPhone")
    List<CV> findByNormalizedPhoneNumber(@Param("normalizedPhone") String normalizedPhone);

    // Name-based queries
    List<CV> findByFullNameContainingIgnoreCase(String fullName);

    @Query("SELECT c FROM CV c WHERE LOWER(c.fullName) LIKE LOWER(CONCAT('%', :name, '%'))")
    List<CV> findByNameContaining(@Param("name") String name);

//    // Content-based full-text search
//    @Query("SELECT c FROM CV c WHERE LOWER(c.content) LIKE LOWER(CONCAT('%', :keyword, '%'))")
//    List<CV> findByContentContaining(@Param("keyword") String keyword);

//    @Query("SELECT c FROM CV c WHERE " +
//            "LOWER(c.content) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
//            "LOWER(c.fullName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
//            "LOWER(c.email) LIKE LOWER(CONCAT('%', :keyword, '%'))")
//    Page<CV> searchByKeyword(@Param("keyword") String keyword, Pageable pageable);

    // Skills-based queries
    @Query("SELECT DISTINCT c FROM CV c JOIN c.skills s WHERE s IN :skills")
    List<CV> findBySkillsIn(@Param("skills") List<String> skills);

    @Query("SELECT c FROM CV c WHERE SIZE(c.skills) >= :minSkills")
    List<CV> findByMinimumSkillCount(@Param("minSkills") int minSkills);

    // Date range queries
    List<CV> findByCreatedDateBetween(LocalDateTime startDate, LocalDateTime endDate);

    @Query("SELECT c FROM CV c WHERE c.createdDate >= :startDate AND c.status = :status")
    List<CV> findRecentByStatus(@Param("startDate") LocalDateTime startDate, @Param("status") CV.CVStatus status);

    // Batch processing queries
    List<CV> findByBatchId(String batchId);

    @Query("SELECT c FROM CV c WHERE c.batchId = :batchId AND c.processingTimeMs > :maxTime")
    List<CV> findSlowProcessingInBatch(@Param("batchId") String batchId, @Param("maxTime") Long maxTime);

    // Duplicate detection queries
    List<CV> findByDuplicateOf(Long duplicateOf);

    @Query("SELECT c FROM CV c WHERE c.status != 'DUPLICATE' ORDER BY c.createdDate ASC")
    List<CV> findAllProcessedCVs();

    List<CV> findAllByOrderByCreatedDateAsc();

    // Advanced filtering with multiple criteria
    @Query("SELECT c FROM CV c WHERE " +
            "(:status IS NULL OR c.status = :status) AND " +
            "(:fileType IS NULL OR c.fileType = :fileType) AND " +
            "(:startDate IS NULL OR c.createdDate >= :startDate) AND " +
            "(:endDate IS NULL OR c.createdDate <= :endDate)")
    Page<CV> findWithFilters(@Param("status") CV.CVStatus status,
                             @Param("fileType") String fileType,
                             @Param("startDate") LocalDateTime startDate,
                             @Param("endDate") LocalDateTime endDate,
                             Pageable pageable);

    // Aggregation and statistics queries
    @Query("SELECT c.status, COUNT(c) FROM CV c GROUP BY c.status")
    List<Object[]> getStatusStatistics();

    @Query("SELECT c.fileType, COUNT(c), AVG(c.processingTimeMs) FROM CV c GROUP BY c.fileType")
    List<Object[]> getFileTypeStatistics();

    @Query("SELECT DATE(c.createdDate), COUNT(c) FROM CV c WHERE c.createdDate >= :startDate GROUP BY DATE(c.createdDate) ORDER BY DATE(c.createdDate)")
    List<Object[]> getDailyProcessingStats(@Param("startDate") LocalDateTime startDate);

    // Performance metrics
    @Query("SELECT AVG(c.processingTimeMs), MAX(c.processingTimeMs), MIN(c.processingTimeMs) FROM CV c WHERE c.processingTimeMs IS NOT NULL")
    List<Object[]> getProcessingTimeMetrics();

    @Query("SELECT c.batchId, COUNT(c), AVG(c.processingTimeMs) FROM CV c WHERE c.batchId IS NOT NULL GROUP BY c.batchId ORDER BY c.createdDate DESC")
    List<Object[]> getBatchPerformanceMetrics();

    // Top skills analysis
    @Query(value = "SELECT skill, COUNT(*) as count FROM cv_skills GROUP BY skill ORDER BY count DESC LIMIT :limit", nativeQuery = true)
    List<Object[]> getTopSkills(@Param("limit") int limit);

    // Error analysis
    List<CV> findByStatusAndErrorMessageIsNotNull(CV.CVStatus status);

    @Query("SELECT c.errorMessage, COUNT(c) FROM CV c WHERE c.status = 'ERROR' AND c.errorMessage IS NOT NULL GROUP BY c.errorMessage")
    List<Object[]> getErrorStatistics();

    // Shortlisted candidates with specific skills
    @Query("SELECT DISTINCT c FROM CV c JOIN c.skills s WHERE c.status = 'SHORTLISTED' AND s IN :requiredSkills")
    Page<CV> findShortlistedWithSkills(@Param("requiredSkills") List<String> requiredSkills, Pageable pageable);

    // Recent uploads
    @Query("SELECT c FROM CV c WHERE c.createdDate >= :since ORDER BY c.createdDate DESC")
    List<CV> findRecentUploads(@Param("since") LocalDateTime since);

    // File size analysis
    @Query("SELECT AVG(c.fileSize), MAX(c.fileSize), MIN(c.fileSize) FROM CV c WHERE c.fileSize IS NOT NULL")
    List<Object[]> getFileSizeMetrics();

    // Custom search for API endpoints
    @Query("SELECT c FROM CV c WHERE " +
            "(:searchTerm IS NULL OR " +
            " LOWER(c.fullName) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            " LOWER(c.email) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            " LOWER(c.fileName) LIKE LOWER(CONCAT('%', :searchTerm, '%'))) AND " +
            "(:status IS NULL OR c.status = :status)")
    Page<CV> searchCVs(@Param("searchTerm") String searchTerm,
                       @Param("status") CV.CVStatus status,
                       Pageable pageable);
}

