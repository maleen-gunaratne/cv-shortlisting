package com.raketman.shortlisting.controller;

import com.raketman.shortlisting.dto.BatchProcessingRequest;
import com.raketman.shortlisting.dto.CVResponse;
import com.raketman.shortlisting.dto.ServiceResponse;
import com.raketman.shortlisting.entity.CV;
import com.raketman.shortlisting.service.CVProcessingService;
import com.raketman.shortlisting.service.DuplicateDetectionService;
import com.raketman.shortlisting.service.KeywordMatchingService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/cvs")
@CrossOrigin(origins = "*", maxAge = 3600)
@RequiredArgsConstructor
@Validated
public class CVController {

    private final CVProcessingService cvProcessingService;
    private final DuplicateDetectionService duplicateDetectionService;
    private final KeywordMatchingService keywordMatchingService;

    @PostMapping("/process")
    @PreAuthorize("hasRole('ADMIN') or hasRole('HR')")
    public ResponseEntity<ServiceResponse<Map<String, Object>>> processCVs(
            @Valid @RequestBody BatchProcessingRequest request) {

        Map<String, Object> result = cvProcessingService.processCVsFromDirectory(
                request.getInputDirectory(),
                request.getBatchSize(),
                request.isAsync()
        );

        return ResponseEntity.accepted().body(
                new ServiceResponse<>(true, "Batch processing started", result)
        );
    }

    @GetMapping("/shortlisted")
    public ResponseEntity<ServiceResponse<Page<CVResponse>>> getShortlistedCVs(
            @Min(value = 0, message = "Page index must be >= 0") @RequestParam(defaultValue = "0") int page,
            @Min(value = 1, message = "Page size must be >= 1") @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdDate") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir,
            @RequestParam(required = false) List<String> skills) {

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.fromString(sortDir), sortBy));
        Page<CV> cvs = cvProcessingService.getShortlistedCVs(pageable, skills);
        Page<CVResponse> response = cvs.map(this::convertToResponse);

        return ResponseEntity.ok(new ServiceResponse<>(true, "Shortlisted CVs retrieved", response));
    }

    @GetMapping("/duplicates")
    public ResponseEntity<ServiceResponse<Page<CVResponse>>> getDuplicateCVs(
            @Min(0) @RequestParam(defaultValue = "0") int page,
            @Min(1) @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdDate"));
        Page<CV> duplicates = cvProcessingService.getDuplicateCVs(pageable);
        Page<CVResponse> response = duplicates.map(this::convertToResponse);

        return ResponseEntity.ok(new ServiceResponse<>(true, "Duplicate CVs retrieved", response));
    }

    @GetMapping("/search")
    public ResponseEntity<ServiceResponse<Page<CVResponse>>> searchCVs(
            @NotBlank(message = "Search query cannot be empty") @RequestParam String query,
            @RequestParam(required = false) CV.CVStatus status,
            @Min(0) @RequestParam(defaultValue = "0") int page,
            @Min(1) @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdDate"));
        Page<CV> results = cvProcessingService.searchCVs(query, status, pageable);
        Page<CVResponse> response = results.map(this::convertToResponse);

        return ResponseEntity.ok(new ServiceResponse<>(true, "Search results retrieved", response));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ServiceResponse<CVResponse>> getCVById(@PathVariable Long id) {
        CV cv = cvProcessingService.getCVById(id);
        if (cv == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(new ServiceResponse<>(true, "CV retrieved", convertToResponse(cv)));
    }

    @GetMapping("/stats/duplicates")
    public ResponseEntity<ServiceResponse<DuplicateDetectionService.DuplicateStats>> getDuplicateStats() {
        DuplicateDetectionService.DuplicateStats stats = duplicateDetectionService.getDuplicateStats();
        return ResponseEntity.ok(new ServiceResponse<>(true, "Duplicate stats retrieved", stats));
    }

    @GetMapping("/skills")
    public ResponseEntity<ServiceResponse<Map<String, Set<String>>>> getAvailableSkills() {
        Map<String, Set<String>> skills = keywordMatchingService.getAvailableSkills();
        return ResponseEntity.ok(new ServiceResponse<>(true, "Available skills retrieved", skills));
    }

    @GetMapping("/config/matching")
    public ResponseEntity<ServiceResponse<Map<String, Object>>> getMatchingConfig() {
        Map<String, Object> config = keywordMatchingService.getMatchingConfiguration();
        return ResponseEntity.ok(new ServiceResponse<>(true, "Matching configuration retrieved", config));
    }

    @PostMapping("/reprocess-duplicates")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ServiceResponse<String>> reprocessDuplicates() {
        duplicateDetectionService.reprocessDuplicatesInBatch();
        return ResponseEntity.ok(new ServiceResponse<>(true, "Duplicate reprocessing completed successfully", null));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteCV(@PathVariable Long id) {
        boolean deleted = cvProcessingService.deleteCV(id);
        return deleted ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN') or hasRole('HR')")
    public ResponseEntity<ServiceResponse<CVResponse>> updateCVStatus(
            @PathVariable Long id,
            @RequestParam CV.CVStatus status) {

        CV updatedCV = cvProcessingService.updateCVStatus(id, status);
        if (updatedCV == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(new ServiceResponse<>(true, "CV status updated", convertToResponse(updatedCV)));
    }

    /**
     * Convert CV entity to response DTO
     */
    private CVResponse convertToResponse(CV cv) {
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

        if (cv.getContent() != null && cv.getContent().length() > 500) {
            response.setContentPreview(cv.getContent().substring(0, 500) + "...");
        } else {
            response.setContentPreview(cv.getContent());
        }

        return response;
    }
}
