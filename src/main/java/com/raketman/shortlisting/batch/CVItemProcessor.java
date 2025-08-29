package com.raketman.shortlisting.batch;

import com.raketman.shortlisting.entity.CV;
import com.raketman.shortlisting.service.DocumentParserService;
import com.raketman.shortlisting.service.DuplicateDetectionService;
import com.raketman.shortlisting.service.KeywordMatchingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class CVItemProcessor implements ItemProcessor<File, CV> {

    private static final Logger logger = LoggerFactory.getLogger(CVItemProcessor.class);

    @Autowired
    private DocumentParserService documentParserService;

    @Autowired
    private KeywordMatchingService keywordMatchingService;

    @Autowired
    private DuplicateDetectionService duplicateDetectionService;

    @Override
    public CV process(File file) throws Exception {
        long startTime = System.currentTimeMillis();
        logger.info("Starting processing of CV: {}", file.getName());


            CV cv = initializeCV(file);

            String content = documentParserService.parseDocument(file);
            cv.setContent(content);

            populatePersonalInfo(cv, content);
            ensureRequiredFields(cv);

            extractSkillsAndMatchCriteria(cv, content);
            detectDuplicates(cv);

            cv.setProcessingTimeMs(System.currentTimeMillis() - startTime);

            logger.info("Completed processing of CV: {} | Status: {} | Skills found: {} | Time: {}ms",
                    cv.getFileName(), cv.getStatus(), cv.getSkills().size(), cv.getProcessingTimeMs());

            return cv;

    }

    private CV initializeCV(File file) {
        CV cv = new CV();
        cv.setFileName(file.getName());
        cv.setFilePath(file.getAbsolutePath());
        cv.setFileSize(file.length());
        cv.setFileType(getFileExtension(file.getName()));
        cv.setBatchId(getCurrentBatchId());
        cv.setStatus(CV.CVStatus.PROCESSING);
        cv.setProcessedBy(Thread.currentThread().getName());

        logger.debug("Initialized CV object for file: {}", file.getName());
        return cv;
    }

    private void populatePersonalInfo(CV cv, String content) {
        cv.setEmail(documentParserService.extractEmail(content));
        cv.setPhoneNumber(documentParserService.extractPhoneNumber(content));
        cv.setFullName(documentParserService.extractName(content));

        logger.debug("Extracted personal info for {}: fullName={}, email={}, phone={}",
                cv.getFileName(), cv.getFullName(), cv.getEmail(), cv.getPhoneNumber());
    }

    private void extractSkillsAndMatchCriteria(CV cv, String content) {
        Set<String> skills = keywordMatchingService.extractSkills(content);
        cv.setSkills(skills);

        if (cv.getStatus() != CV.CVStatus.DUPLICATE) {
            boolean isShortlisted = keywordMatchingService.matchesCriteria(cv);
            cv.setStatus(isShortlisted ? CV.CVStatus.SHORTLISTED : CV.CVStatus.REJECTED);
            logger.debug("CV {} evaluated against criteria: shortlisted={}, skillsCount={}",
                    cv.getFileName(), isShortlisted, skills.size());
        }
    }

    private void detectDuplicates(CV cv) {
        Optional.ofNullable(duplicateDetectionService.findDuplicate(cv)).ifPresent(duplicateCV -> {
            cv.setStatus(CV.CVStatus.DUPLICATE);
            cv.setDuplicateOf(duplicateCV.getId());
            cv.setSimilarityScore(duplicateDetectionService.calculateSimilarityScore(cv, duplicateCV));
            logger.info("Duplicate detected: {} is similar to CV ID {} with similarity {}",
                    cv.getFileName(), duplicateCV.getId(), cv.getSimilarityScore());
        });
    }

    private CV createErrorCV(File file, Exception e, long startTime) {
        CV errorCV = initializeCV(file);
        errorCV.setStatus(CV.CVStatus.ERROR);
        errorCV.setErrorMessage(e.getMessage());
        errorCV.setProcessingTimeMs(System.currentTimeMillis() - startTime);
        ensureRequiredFields(errorCV);

        logger.warn("Created error CV record for file: {} | Error: {}", file.getName(), e.getMessage());
        return errorCV;
    }

    private void ensureRequiredFields(CV cv) {
        cv.setFullName(Optional.ofNullable(cv.getFullName())
                .filter(name -> !name.trim().isEmpty())
                .orElseGet(() -> Optional.ofNullable(extractNameFromFilename(cv.getFileName()))
                        .orElseGet(() -> fallbackNameFromFile(cv.getFileName()))));

        cv.setEmail(Optional.ofNullable(cv.getEmail()).filter(s -> !s.trim().isEmpty())
                .orElse("unknown@example.com"));
        cv.setPhoneNumber(Optional.ofNullable(cv.getPhoneNumber()).filter(s -> !s.trim().isEmpty())
                .orElse("N/A"));

        // Trim whitespaces
        cv.setFullName(cv.getFullName().trim());
        cv.setEmail(cv.getEmail().trim());
        cv.setPhoneNumber(cv.getPhoneNumber().trim());
    }

    private String extractNameFromFilename(String filename) {
        if (filename == null || filename.trim().isEmpty()) return null;

        String nameWithoutExtension = filename.replaceAll("\\.[^.]+$", "");
        String[] patterns = {
                "([A-Z][a-z]+\\s+[A-Z][a-z]+)_.*",
                "([A-Z][a-z]+)_([A-Z][a-z]+)_.*",
                "([A-Z][a-z]+)([A-Z][a-z]+)-.*",
                "([A-Z][a-z]+\\s+[A-Z][a-z]+)",
                "([A-Z][a-z]+_[A-Z][a-z]+)",
                "([A-Z][a-z]+-[A-Z][a-z]+)"
        };

        return Arrays.stream(patterns)
                .map(Pattern::compile)
                .map(p -> p.matcher(nameWithoutExtension))
                .filter(Matcher::find)
                .map(matcher -> {
                    String name = matcher.group(1);
                    return matcher.groupCount() > 1 && matcher.group(2) != null
                            ? name + " " + matcher.group(2)
                            : name;
                })
                .map(s -> s.replaceAll("[_-]", " ").trim())
                .findFirst()
                .orElseGet(() -> {
                    String[] parts = nameWithoutExtension.split("[_\\-\\s]+");
                    return parts.length >= 2 ? parts[0] + " " + parts[1] : fallbackNameFromFile(filename);
                });
    }

    private String fallbackNameFromFile(String filename) {
        return filename.replaceAll("\\.[^.]+$", "")
                .replaceAll("[_\\-]", " ").trim();
    }

    private String getFileExtension(String fileName) {
        return Optional.ofNullable(fileName)
                .filter(f -> !f.isEmpty())
                .map(f -> {
                    int lastDotIndex = f.lastIndexOf('.');
                    return lastDotIndex > 0 ? f.substring(lastDotIndex + 1).toLowerCase() : "";
                })
                .orElse("");
    }

    private String getCurrentBatchId() {
        return "batch-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
