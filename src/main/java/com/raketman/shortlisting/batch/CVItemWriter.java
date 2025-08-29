package com.raketman.shortlisting.batch;

import com.raketman.shortlisting.entity.CV;
import com.raketman.shortlisting.repository.CVRepository;
import com.raketman.shortlisting.service.FileOrganizationService;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class CVItemWriter implements ItemWriter<CV> {

    private static final Logger logger = LoggerFactory.getLogger(CVItemWriter.class);

    @Autowired
    private CVRepository cvRepository;

    @Autowired
    private FileOrganizationService fileOrganizationService;

    @Override
    @Transactional
    public void write(Chunk<? extends CV> chunk) throws Exception {
        List<? extends CV> cvs = chunk.getItems();

        if (cvs == null || cvs.isEmpty()) {
            logger.debug("No CVs to write in this chunk");
            return;
        }

        logger.info("Writing batch of {} CVs to database", cvs.size());
        long startTime = System.currentTimeMillis();

        try {
            // Process each CV in the chunk
            for (CV cv : cvs) {
                processAndSaveCV(cv);
            }

            // Organize files after successful database save
            organizeProcessedFiles(cvs);

            long processingTime = System.currentTimeMillis() - startTime;
            logger.info("Successfully wrote {} CVs to database in {}ms (avg: {}ms per CV)",
                    cvs.size(), processingTime, processingTime / cvs.size());

        } catch (Exception e) {
            logger.error("Error writing CV batch to database", e);

            // Mark failed CVs with error status
            markCVsAsError(cvs, e.getMessage());

            // Re-throw to let Spring Batch handle the failure
            throw new CVWriteException("Failed to write CV batch: " + e.getMessage(), e);
        }
    }

    /**
     * Process and save individual CV to database
     */
    private void processAndSaveCV(CV cv) {
        try {
            // Set timestamps if not already set
            if (cv.getCreatedDate() == null) {
                cv.setCreatedDate(LocalDateTime.now());
            }
            cv.setLastModifiedDate(LocalDateTime.now());

            // Validate CV data before saving
            validateCV(cv);

            // Save CV to database
            CV savedCV = cvRepository.save(cv);

            logger.debug("Saved CV {} with ID {} (Status: {})",
                    cv.getFileName(), savedCV.getId(), savedCV.getStatus());

        } catch (Exception e) {
            logger.error("Error saving CV {} to database: {}", cv.getFileName(), e.getMessage(), e);

            // Update CV with error status
            cv.setStatus(CV.CVStatus.ERROR);
            cv.setErrorMessage("Database save error: " + e.getMessage());
            cv.setLastModifiedDate(LocalDateTime.now());

            // Try to save error record
            try {
                cvRepository.save(cv);
            } catch (Exception saveErrorException) {
                logger.error("Failed to save error record for CV {}: {}",
                        cv.getFileName(), saveErrorException.getMessage());
            }
        }
    }

    /**
     * Organize processed files into appropriate directories
     */
    private void organizeProcessedFiles(List<? extends CV> cvs) {
        logger.debug("Organizing {} processed files", cvs.size());

        for (CV cv : cvs) {
            try {
                fileOrganizationService.organizeFile(cv);
            } catch (Exception e) {
                logger.warn("Failed to organize file {} after processing: {}",
                        cv.getFileName(), e.getMessage());
                // Don't fail the entire batch for file organization issues
            }
        }
    }

    /**
     * Mark CVs as error status in case of batch failure
     */
    private void markCVsAsError(List<? extends CV> cvs, String errorMessage) {
        for (CV cv : cvs) {
            try {
                cv.setStatus(CV.CVStatus.ERROR);
                cv.setErrorMessage("Batch write error: " + errorMessage);
                cv.setLastModifiedDate(LocalDateTime.now());
                cvRepository.save(cv);
            } catch (Exception e) {
                logger.error("Failed to mark CV {} as error: {}", cv.getFileName(), e.getMessage());
            }
        }
    }

    /**
     * Validate CV data before database save
     */
    private void validateCV(CV cv) {
        if (cv.getFileName() == null || cv.getFileName().trim().isEmpty()) {
            throw new IllegalArgumentException("CV filename cannot be null or empty");
        }

        if (cv.getFilePath() == null || cv.getFilePath().trim().isEmpty()) {
            throw new IllegalArgumentException("CV file path cannot be null or empty");
        }

        if (cv.getStatus() == null) {
            throw new IllegalArgumentException("CV status cannot be null");
        }

        // Validate email format if present
        if (cv.getEmail() != null && !cv.getEmail().trim().isEmpty()) {
            if (!isValidEmail(cv.getEmail())) {
                logger.warn("Invalid email format for CV {}: {}", cv.getFileName(), cv.getEmail());
                cv.setEmail(null); // Clear invalid email
            }
        }

        // Validate phone number format if present
        if (cv.getPhoneNumber() != null && !cv.getPhoneNumber().trim().isEmpty()) {
            if (!isValidPhoneNumber(cv.getPhoneNumber())) {
                logger.warn("Invalid phone format for CV {}: {}", cv.getFileName(), cv.getPhoneNumber());
                cv.setPhoneNumber(null); // Clear invalid phone
            }
        }

        // Ensure content is not too large for database
        if (cv.getContent() != null && cv.getContent().length() > 1000000) { // 1MB limit
            logger.warn("CV content too large for {}, truncating", cv.getFileName());
            cv.setContent(cv.getContent().substring(0, 1000000) + "... [TRUNCATED]");
        }
    }

    /**
     * Simple email validation
     */
    private boolean isValidEmail(String email) {
        return email != null &&
                email.contains("@") &&
                email.contains(".") &&
                email.length() > 5 &&
                email.length() < 255;
    }

    /**
     * Simple phone number validation
     */
    private boolean isValidPhoneNumber(String phone) {
        if (phone == null) return false;

        // Remove all non-digit characters for validation
        String digitsOnly = phone.replaceAll("[^0-9]", "");

        // Should have between 7 and 15 digits
        return digitsOnly.length() >= 7 && digitsOnly.length() <= 15;
    }

    /**
     * Get statistics for current write operation
     */
    public CVWriteStatistics getWriteStatistics(List<? extends CV> cvs) {
        if (cvs == null || cvs.isEmpty()) {
            return new CVWriteStatistics(0, 0, 0, 0, 0);
        }

        long shortlisted = cvs.stream().filter(cv -> cv.getStatus() == CV.CVStatus.SHORTLISTED).count();
        long duplicates = cvs.stream().filter(cv -> cv.getStatus() == CV.CVStatus.DUPLICATE).count();
        long rejected = cvs.stream().filter(cv -> cv.getStatus() == CV.CVStatus.REJECTED).count();
        long errors = cvs.stream().filter(cv -> cv.getStatus() == CV.CVStatus.ERROR).count();

        return new CVWriteStatistics(cvs.size(), shortlisted, duplicates, rejected, errors);
    }



    /**
     * Statistics class for write operations
     */
    public static class CVWriteStatistics {
        private final long total;
        private final long shortlisted;
        private final long duplicates;
        private final long rejected;
        private final long errors;

        public CVWriteStatistics(long total, long shortlisted, long duplicates, long rejected, long errors) {
            this.total = total;
            this.shortlisted = shortlisted;
            this.duplicates = duplicates;
            this.rejected = rejected;
            this.errors = errors;
        }

        public long getTotal() { return total; }
        public long getShortlisted() { return shortlisted; }
        public long getDuplicates() { return duplicates; }
        public long getRejected() { return rejected; }
        public long getErrors() { return errors; }

        @Override
        public String toString() {
            return String.format("CVWriteStatistics{total=%d, shortlisted=%d, duplicates=%d, rejected=%d, errors=%d}",
                    total, shortlisted, duplicates, rejected, errors);
        }
    }

    /**
     * Custom exception for write operations
     */
    public static class CVWriteException extends RuntimeException {
        public CVWriteException(String message) {
            super(message);
        }

        public CVWriteException(String message, Throwable cause) {
            super(message, cause);
        }
    }


}
