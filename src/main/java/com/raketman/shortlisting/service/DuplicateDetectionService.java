package com.raketman.shortlisting.service;

import com.raketman.shortlisting.entity.CV;
import com.raketman.shortlisting.repository.CVRepository;
import me.xdrop.fuzzywuzzy.FuzzySearch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
public class DuplicateDetectionService {

    private static final Logger logger = LoggerFactory.getLogger(DuplicateDetectionService.class);

    @Autowired
    private CVRepository cvRepository;

    @Value("${cv.duplicate.threshold.exact:95}")
    private int exactMatchThreshold;

    @Value("${cv.duplicate.threshold.fuzzy:85}")
    private int fuzzyMatchThreshold;

    @Value("${cv.duplicate.threshold.partial:75}")
    private int partialMatchThreshold;

    public CV findDuplicate(CV candidateCV) {
        if (candidateCV == null) {
            return null;
        }

        // First, check for exact email matches (highest priority)
        if (candidateCV.getEmail() != null && !candidateCV.getEmail().trim().isEmpty()) {
            List<CV> emailMatches = cvRepository.findByEmailIgnoreCase(candidateCV.getEmail().trim());
            if (!emailMatches.isEmpty()) {
                CV match = emailMatches.get(0);
                logger.info("Exact email match found: {} matches {}",
                        candidateCV.getEmail(), match.getEmail());
                return match;
            }
        }

        // Second, check for exact phone number matches
        if (candidateCV.getPhoneNumber() != null && !candidateCV.getPhoneNumber().trim().isEmpty()) {
            String normalizedPhone = normalizePhoneNumber(candidateCV.getPhoneNumber());
            List<CV> phoneMatches = cvRepository.findByNormalizedPhoneNumber(normalizedPhone);
            if (!phoneMatches.isEmpty()) {
                CV match = phoneMatches.get(0);
                logger.info("Exact phone match found: {} matches {}",
                        candidateCV.getPhoneNumber(), match.getPhoneNumber());
                return match;
            }
        }

        // Third, fuzzy matching on names
        if (candidateCV.getFullName() != null && !candidateCV.getFullName().trim().isEmpty()) {
            List<CV> allCVs = cvRepository.findAllProcessedCVs();
            return findFuzzyNameMatch(candidateCV, allCVs);
        }

        return null;
    }

    /**
     * Find fuzzy name matches using advanced string similarity algorithms
     */
    @Cacheable(value = "fuzzyMatches", key = "#candidateCV.fullName")
    private CV findFuzzyNameMatch(CV candidateCV, List<CV> existingCVs) {
        String candidateName = candidateCV.getFullName().trim().toLowerCase();

        for (CV existingCV : existingCVs) {
            if (existingCV.getFullName() == null || existingCV.getFullName().trim().isEmpty()) {
                continue;
            }

            String existingName = existingCV.getFullName().trim().toLowerCase();

            // Skip if same object or if names are too different in length
            if (Objects.equals(candidateCV.getId(), existingCV.getId()) ||
                    Math.abs(candidateName.length() - existingName.length()) > 10) {
                continue;
            }

            // Calculate multiple similarity scores
            int ratio = FuzzySearch.ratio(candidateName, existingName);
            int partialRatio = FuzzySearch.partialRatio(candidateName, existingName);
            int tokenSortRatio = FuzzySearch.tokenSortRatio(candidateName, existingName);
            int tokenSetRatio = FuzzySearch.tokenSetRatio(candidateName, existingName);

            // Calculate weighted average
            double weightedScore = (ratio * 0.3 + partialRatio * 0.2 +
                    tokenSortRatio * 0.25 + tokenSetRatio * 0.25);

            logger.debug("Name similarity for '{}' vs '{}': ratio={}, partial={}, tokenSort={}, tokenSet={}, weighted={}",
                    candidateName, existingName, ratio, partialRatio, tokenSortRatio, tokenSetRatio, weightedScore);

            // Check if it meets our threshold criteria
            if (weightedScore >= exactMatchThreshold ||
                    (ratio >= fuzzyMatchThreshold && tokenSetRatio >= fuzzyMatchThreshold) ||
                    (partialRatio >= partialMatchThreshold && tokenSortRatio >= partialMatchThreshold)) {

                // Additional validation with email/phone if available
                if (hasAdditionalSimilarity(candidateCV, existingCV)) {
                    logger.info("Fuzzy name match found: '{}' matches '{}' (score: {})",
                            candidateName, existingName, weightedScore);
                    return existingCV;
                }
            }
        }

        return null;
    }

    /**
     * Check for additional similarity indicators beyond name matching
     */
    private boolean hasAdditionalSimilarity(CV candidate, CV existing) {
        // If both have emails, they should be similar
        if (candidate.getEmail() != null && existing.getEmail() != null &&
                !candidate.getEmail().isEmpty() && !existing.getEmail().isEmpty()) {

            String candidateEmail = candidate.getEmail().toLowerCase();
            String existingEmail = existing.getEmail().toLowerCase();

            // Extract username part before @
            String candidateUsername = candidateEmail.split("@")[0];
            String existingUsername = existingEmail.split("@")[0];

            int emailSimilarity = FuzzySearch.ratio(candidateUsername, existingUsername);
            if (emailSimilarity >= 80) {
                return true;
            }
        }

        // If both have phone numbers, they should be similar
        if (candidate.getPhoneNumber() != null && existing.getPhoneNumber() != null &&
                !candidate.getPhoneNumber().isEmpty() && !existing.getPhoneNumber().isEmpty()) {

            String candidatePhone = normalizePhoneNumber(candidate.getPhoneNumber());
            String existingPhone = normalizePhoneNumber(existing.getPhoneNumber());

            // Check if last 7 digits match (common for same person with different area codes)
            if (candidatePhone.length() >= 7 && existingPhone.length() >= 7) {
                String candidateLast7 = candidatePhone.substring(candidatePhone.length() - 7);
                String existingLast7 = existingPhone.substring(existingPhone.length() - 7);

                if (candidateLast7.equals(existingLast7)) {
                    return true;
                }
            }
        }

        // If no email or phone available, rely purely on name similarity
        return candidate.getEmail() == null && candidate.getPhoneNumber() == null;
    }

    /**
     * Calculate comprehensive similarity score between two CVs
     */
    public double calculateSimilarityScore(CV cv1, CV cv2) {
        if (cv1 == null || cv2 == null) {
            return 0.0;
        }

        double totalScore = 0.0;
        int factors = 0;

        // Name similarity (weight: 40%)
        if (cv1.getFullName() != null && cv2.getFullName() != null) {
            int nameScore = FuzzySearch.tokenSetRatio(
                    cv1.getFullName().toLowerCase(),
                    cv2.getFullName().toLowerCase()
            );
            totalScore += nameScore * 0.4;
            factors++;
        }

        // Email similarity (weight: 35%)
        if (cv1.getEmail() != null && cv2.getEmail() != null) {
            if (cv1.getEmail().equalsIgnoreCase(cv2.getEmail())) {
                totalScore += 100 * 0.35;
            } else {
                int emailScore = FuzzySearch.ratio(
                        cv1.getEmail().toLowerCase(),
                        cv2.getEmail().toLowerCase()
                );
                totalScore += emailScore * 0.35;
            }
            factors++;
        }

        // Phone similarity (weight: 25%)
        if (cv1.getPhoneNumber() != null && cv2.getPhoneNumber() != null) {
            String phone1 = normalizePhoneNumber(cv1.getPhoneNumber());
            String phone2 = normalizePhoneNumber(cv2.getPhoneNumber());

            if (phone1.equals(phone2)) {
                totalScore += 100 * 0.25;
            } else {
                int phoneScore = FuzzySearch.ratio(phone1, phone2);
                totalScore += phoneScore * 0.25;
            }
            factors++;
        }

        return factors > 0 ? totalScore / factors : 0.0;
    }

    /**
     * Normalize phone number for consistent comparison
     * Removes all non-digit characters and handles international formats
     */
    private String normalizePhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isEmpty()) {
            return "";
        }

        // Remove all non-digit characters
        String digitsOnly = phoneNumber.replaceAll("[^0-9]", "");

        // Handle Sri Lankan numbers (+94)
        if (digitsOnly.startsWith("94") && digitsOnly.length() >= 11) {
            return digitsOnly.substring(2); // Remove country code
        }

        // Handle US numbers (+1)
        if (digitsOnly.startsWith("1") && digitsOnly.length() == 11) {
            return digitsOnly.substring(1); // Remove country code
        }

        return digitsOnly;
    }

    /**
     * Get duplicate detection statistics
     */
    public DuplicateStats getDuplicateStats() {
        long totalCVs = cvRepository.count();
        long duplicates = cvRepository.countByStatus(CV.CVStatus.DUPLICATE);
        long unique = totalCVs - duplicates;

        return new DuplicateStats(totalCVs, unique, duplicates,
                totalCVs > 0 ? (double) duplicates / totalCVs * 100 : 0.0);
    }

    /**
     * Batch process duplicate detection for existing CVs
     */
    public void reprocessDuplicatesInBatch() {
        logger.info("Starting batch duplicate detection process");

        List<CV> allCVs = cvRepository.findAllByOrderByCreatedDateAsc();
        int processedCount = 0;
        int duplicatesFound = 0;

        for (CV cv : allCVs) {
            if (cv.getStatus() == CV.CVStatus.DUPLICATE) {
                continue; // Skip already identified duplicates
            }

            // Reset status and check for duplicates
            cv.setStatus(CV.CVStatus.PROCESSING);
            CV duplicate = findDuplicate(cv);

            if (duplicate != null) {
                cv.setStatus(CV.CVStatus.DUPLICATE);
                cv.setDuplicateOf(duplicate.getId());
                cv.setSimilarityScore(calculateSimilarityScore(cv, duplicate));
                duplicatesFound++;
            } else {
                // Restore original status if not duplicate
                cv.setStatus(cv.getSkills() != null && !cv.getSkills().isEmpty() ?
                        CV.CVStatus.SHORTLISTED : CV.CVStatus.REJECTED);
            }

            cvRepository.save(cv);
            processedCount++;
        }

        logger.info("Batch duplicate detection completed. Processed: {}, Duplicates found: {}",
                processedCount, duplicatesFound);
    }

    /**
     * Data class for duplicate detection statistics
     */
    public static class DuplicateStats {
        private final long totalCVs;
        private final long uniqueCVs;
        private final long duplicateCVs;
        private final double duplicatePercentage;

        public DuplicateStats(long totalCVs, long uniqueCVs, long duplicateCVs, double duplicatePercentage) {
            this.totalCVs = totalCVs;
            this.uniqueCVs = uniqueCVs;
            this.duplicateCVs = duplicateCVs;
            this.duplicatePercentage = duplicatePercentage;
        }

        public long getTotalCVs() { return totalCVs; }
        public long getUniqueCVs() { return uniqueCVs; }
        public long getDuplicateCVs() { return duplicateCVs; }
        public double getDuplicatePercentage() { return duplicatePercentage; }
    }



}
