package com.raketman.shortlisting.service;

import com.raketman.shortlisting.entity.CV;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Service for organizing processed CV files into appropriate directories
 * based on their processing status (shortlisted, duplicates, others)
 */
@Service
public class FileOrganizationService {

    private static final Logger logger = LoggerFactory.getLogger(FileOrganizationService.class);

    @Value("${cv.directories.output:./data/output}")
    private String outputBaseDirectory;

    @Value("${cv.directories.shortlisted:./data/output/shortlisted}")
    private String shortlistedDirectory;

    @Value("${cv.directories.duplicates:./data/output/duplicates}")
    private String duplicatesDirectory;

    @Value("${cv.directories.others:./data/output/others}")
    private String othersDirectory;

    @Value("${cv.directories.errors:./data/output/errors}")
    private String errorsDirectory;

    @Value("${cv.file.organization.enabled:true}")
    private boolean fileOrganizationEnabled;

    @Value("${cv.file.organization.create-date-folders:true}")
    private boolean createDateFolders;

    /**
     * Organize a CV file based on its processing status
     */
    public void organizeFile(CV cv) throws FileOrganizationException {
        if (!fileOrganizationEnabled) {
            logger.debug("File organization is disabled, skipping file: {}", cv.getFileName());
            return;
        }

        if (cv.getFilePath() == null || cv.getFilePath().isEmpty()) {
            logger.warn("CV {} has no file path, cannot organize", cv.getFileName());
            return;
        }

        try {
            File sourceFile = new File(cv.getFilePath());
            if (!sourceFile.exists()) {
                logger.warn("Source file does not exist: {}", cv.getFilePath());
                return;
            }

            String targetDirectory = determineTargetDirectory(cv.getStatus());
            Path targetPath = createTargetPath(targetDirectory, cv.getFileName());

            // Ensure target directory exists
            createDirectoryIfNotExists(targetPath.getParent());

            // Move the file
            moveFile(sourceFile.toPath(), targetPath);

            logger.info("Organized CV file {} to {}", cv.getFileName(), targetPath.toString());

        } catch (Exception e) {
            throw new FileOrganizationException(
                    "Failed to organize file " + cv.getFileName() + ": " + e.getMessage(), e);
        }
    }

    /**
     * Determine target directory based on CV status
     */

    private String determineTargetDirectory(CV.CVStatus status) {
        switch (status) {
            case SHORTLISTED:
                return shortlistedDirectory;
            case DUPLICATE:
                return duplicatesDirectory;
            case ERROR:
                return errorsDirectory;
            case REJECTED:
            case PENDING:
            case PROCESSING:
            default:
                return othersDirectory;
        }
    }

    /**
     * Create target path with optional date-based subdirectories
     */
    private Path createTargetPath(String targetDirectory, String fileName) {
        Path basePath = Paths.get(targetDirectory);

        if (createDateFolders) {
            // Create date-based subdirectory (YYYY-MM-DD)
            String dateFolder = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
            basePath = basePath.resolve(dateFolder);
        }

        return basePath.resolve(fileName);
    }

    /**
     * Create directory if it doesn't exist
     */
    private void createDirectoryIfNotExists(Path directoryPath) throws IOException {
        if (!Files.exists(directoryPath)) {
            Files.createDirectories(directoryPath);
            logger.debug("Created directory: {}", directoryPath);
        }
    }

    /**
     * Move file from source to target with conflict resolution
     */
    private void moveFile(Path sourcePath, Path targetPath) throws IOException {
        // Handle file name conflicts
        Path finalTargetPath = resolveFileNameConflicts(targetPath);

        // Move the file
        Files.move(sourcePath, finalTargetPath, StandardCopyOption.REPLACE_EXISTING);
        logger.debug("Moved file from {} to {}", sourcePath, finalTargetPath);
    }

    /**
     * Resolve file name conflicts by appending a number
     */
    private Path resolveFileNameConflicts(Path targetPath) {
        if (!Files.exists(targetPath)) {
            return targetPath;
        }

        Path parent = targetPath.getParent();
        String fileName = targetPath.getFileName().toString();
        String nameWithoutExtension = getNameWithoutExtension(fileName);
        String extension = getFileExtension(fileName);

        int counter = 1;
        Path newPath;
        do {
            String newFileName = nameWithoutExtension + "_" + counter + extension;
            newPath = parent.resolve(newFileName);
            counter++;
        } while (Files.exists(newPath) && counter < 1000); // Prevent infinite loop

        return newPath;
    }

    /**
     * Get file name without extension
     */
    private String getNameWithoutExtension(String fileName) {
        int lastDotIndex = fileName.lastIndexOf('.');
        return lastDotIndex > 0 ? fileName.substring(0, lastDotIndex) : fileName;
    }

    /**
     * Get file extension including the dot
     */
    private String getFileExtension(String fileName) {
        int lastDotIndex = fileName.lastIndexOf('.');
        return lastDotIndex > 0 ? fileName.substring(lastDotIndex) : "";
    }

    /**
     * Initialize all required directories
     */
    public void initializeDirectories() throws FileOrganizationException {
        try {
            createDirectoryIfNotExists(Paths.get(outputBaseDirectory));
            createDirectoryIfNotExists(Paths.get(shortlistedDirectory));
            createDirectoryIfNotExists(Paths.get(duplicatesDirectory));
            createDirectoryIfNotExists(Paths.get(othersDirectory));
            createDirectoryIfNotExists(Paths.get(errorsDirectory));

            logger.info("Initialized CV organization directories");
        } catch (IOException e) {
            throw new FileOrganizationException("Failed to initialize directories: " + e.getMessage(), e);
        }
    }

    /**
     * Get organization statistics
     */
    public FileOrganizationStatistics getOrganizationStatistics() {
        FileOrganizationStatistics stats = new FileOrganizationStatistics();

        try {
            stats.setShortlistedCount(countFilesInDirectory(shortlistedDirectory));
            stats.setDuplicatesCount(countFilesInDirectory(duplicatesDirectory));
            stats.setOthersCount(countFilesInDirectory(othersDirectory));
            stats.setErrorsCount(countFilesInDirectory(errorsDirectory));
        } catch (Exception e) {
            logger.warn("Error collecting organization statistics: {}", e.getMessage());
        }

        return stats;
    }

    /**
     * Count files in a directory recursively
     */
    private long countFilesInDirectory(String directoryPath) throws IOException {
        Path path = Paths.get(directoryPath);
        if (!Files.exists(path)) {
            return 0;
        }

        return Files.walk(path)
                .filter(Files::isRegularFile)
                .count();
    }

    /**
     * Clean up empty date directories
     */
    public void cleanupEmptyDirectories() {
        try {
            cleanupEmptyDirectoriesInPath(Paths.get(shortlistedDirectory));
            cleanupEmptyDirectoriesInPath(Paths.get(duplicatesDirectory));
            cleanupEmptyDirectoriesInPath(Paths.get(othersDirectory));
            cleanupEmptyDirectoriesInPath(Paths.get(errorsDirectory));

            logger.info("Cleaned up empty directories");
        } catch (Exception e) {
            logger.warn("Error during directory cleanup: {}", e.getMessage());
        }
    }

    /**
     * Clean up empty directories recursively
     */
    private void cleanupEmptyDirectoriesInPath(Path basePath) throws IOException {
        if (!Files.exists(basePath)) {
            return;
        }

        Files.walk(basePath)
                .filter(Files::isDirectory)
                .sorted((path1, path2) -> path2.getNameCount() - path1.getNameCount()) // Process deepest first
                .forEach(path -> {
                    try {
                        if (!path.equals(basePath) && isDirEmpty(path)) {
                            Files.delete(path);
                            logger.debug("Deleted empty directory: {}", path);
                        }
                    } catch (IOException e) {
                        logger.warn("Could not delete empty directory {}: {}", path, e.getMessage());
                    }
                });
    }

    /**
     * Check if directory is empty
     */
    private boolean isDirEmpty(Path path) throws IOException {
        try (var entries = Files.list(path)) {
            return !entries.findFirst().isPresent();
        }
    }

    /**
     * Statistics class for file organization
     */
    public static class FileOrganizationStatistics {
        private long shortlistedCount = 0;
        private long duplicatesCount = 0;
        private long othersCount = 0;
        private long errorsCount = 0;

        public long getShortlistedCount() { return shortlistedCount; }
        public void setShortlistedCount(long shortlistedCount) { this.shortlistedCount = shortlistedCount; }

        public long getDuplicatesCount() { return duplicatesCount; }
        public void setDuplicatesCount(long duplicatesCount) { this.duplicatesCount = duplicatesCount; }

        public long getOthersCount() { return othersCount; }
        public void setOthersCount(long othersCount) { this.othersCount = othersCount; }

        public long getErrorsCount() { return errorsCount; }
        public void setErrorsCount(long errorsCount) { this.errorsCount = errorsCount; }

        public long getTotalCount() {
            return shortlistedCount + duplicatesCount + othersCount + errorsCount;
        }

        @Override
        public String toString() {
            return String.format("FileOrganizationStatistics{shortlisted=%d, duplicates=%d, others=%d, errors=%d, total=%d}",
                    shortlistedCount, duplicatesCount, othersCount, errorsCount, getTotalCount());
        }
    }

    /**
     * Custom exception for file organization errors
     */
    public static class FileOrganizationException extends Exception {
        public FileOrganizationException(String message) {
            super(message);
        }

        public FileOrganizationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
