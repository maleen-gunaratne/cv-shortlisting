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

    Page<CV> findByStatus(CV.CVStatus status, Pageable pageable);

    long countByStatus(CV.CVStatus status);

    List<CV> findByEmailIgnoreCase(String email);

    @Query("SELECT c FROM CV c WHERE REPLACE(REPLACE(REPLACE(c.phoneNumber, ' ', ''), '-', ''), '+', '') = :normalizedPhone")
    List<CV> findByNormalizedPhoneNumber(@Param("normalizedPhone") String normalizedPhone);

    List<CV> findByBatchId(String batchId);

    @Query("SELECT c FROM CV c WHERE c.status != 'DUPLICATE' ORDER BY c.createdDate ASC")
    List<CV> findAllProcessedCVs();

    List<CV> findAllByOrderByCreatedDateAsc();

    @Query("SELECT DISTINCT c FROM CV c JOIN c.skills s WHERE c.status = 'SHORTLISTED' AND s IN :requiredSkills")
    Page<CV> findShortlistedWithSkills(@Param("requiredSkills") List<String> requiredSkills, Pageable pageable);

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

