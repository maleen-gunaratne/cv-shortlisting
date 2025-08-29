package com.raketman.shortlisting.service;

import com.raketman.shortlisting.exception.DocumentParsingException;
import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class DocumentParserService {

    private static final Logger logger = LoggerFactory.getLogger(DocumentParserService.class);

    private final Tika tika;

    // Regex patterns for extracting personal information
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Z|a-z]{2,}\\b"
    );

    private static final Pattern PHONE_PATTERN = Pattern.compile(

                    // Handle the specific corruption pattern we're seeing: ?1?555?234?7890
                       "\\?1\\?\\d{3}\\?\\d{3}\\?\\d{4}|" +
                    // More general corrupted patterns
                    "[\\+\\?]1[\\-\\.\\s\\?]\\d{3}[\\-\\.\\s\\?]\\d{3}[\\-\\.\\s\\?]\\d{4}|" +
                    // Sri Lanka formats with potential corruption
                    "[\\+\\?]94[\\-\\.\\s\\?]\\d{2}[\\-\\.\\s\\?]\\d{7}|" +
                    "[\\+\\?]94[\\-\\.\\s\\?]\\d{3}[\\-\\.\\s\\?]\\d{6}|" +
                    // Standard patterns without corruption
                    "\\+1[\\-\\.\\s]\\d{3}[\\-\\.\\s]\\d{3}[\\-\\.\\s]\\d{4}|" +
                    "\\d{3}[\\-\\.\\s]\\d{3}[\\-\\.\\s]\\d{4}"
    );


    private static final Pattern NAME_PATTERN = Pattern.compile(
            "^([A-Z][a-z]+ [A-Z][a-z]+(?:\\s+[A-Z][a-z]+)*)"
    );

    private static final List<String> SUPPORTED_FORMATS = Arrays.asList(
            "pdf", "docx", "doc", "txt", "rtf"
    );

    public DocumentParserService() {
        this.tika = new Tika();
        this.tika.setMaxStringLength(100000);
    }

    /**
     * Parse document and extract text content
     * @param file The file to parse
     * @return Extracted text content
     * @throws DocumentParsingException if parsing fails
     */
    public String parseDocument(File file) throws DocumentParsingException {
        if (file == null || !file.exists()) {
            throw new DocumentParsingException("File does not exist: " +
                    (file != null ? file.getPath() : "null"));
        }

        if (file.length() == 0) {
            throw new DocumentParsingException("File is empty: " + file.getPath());
        }

        if (file.length() > 50 * 1024 * 1024) { // 50MB limit
            throw new DocumentParsingException("File too large: " + file.getPath() +
                    " (" + file.length() + " bytes)");
        }

        String fileExtension = getFileExtension(file.getName()).toLowerCase();
        if (!SUPPORTED_FORMATS.contains(fileExtension)) {
            throw new DocumentParsingException("Unsupported file format: " + fileExtension);
        }

        try (FileInputStream inputStream = new FileInputStream(file)) {
            long startTime = System.currentTimeMillis();
            String content = tika.parseToString(inputStream);
            long parseTime = System.currentTimeMillis() - startTime;

            logger.debug("Parsed {} in {}ms, content length: {}",
                    file.getName(), parseTime, content.length());

            if (content == null || content.trim().isEmpty()) {
                throw new DocumentParsingException("No content extracted from: " + file.getPath());
            }
            return cleanContent(content);

        } catch (IOException e) {
            logger.error("IO error parsing file: {}", file.getPath(), e);
            throw new DocumentParsingException("Failed to read file: " + file.getPath(), e);
        } catch (TikaException e) {
            logger.error("Tika parsing error for file: {}", file.getPath(), e);
            throw new DocumentParsingException("Failed to parse document: " + file.getPath(), e);
        }
    }

    /**
     * Extract email address from content
     * @param content Document content
     * @return First email found, or null if none
     */
    public String extractEmail(String content) {
        if (content == null || content.isEmpty()) {
            return null;
        }

        Matcher matcher = EMAIL_PATTERN.matcher(content);
        return matcher.find() ? matcher.group().toLowerCase() : null;
    }

    /**
     * Extract phone number from content
     * @param content Document content
     * @return First phone number found, or null if none
     */
    public String extractPhoneNumber(String content) {
        if (content == null || content.isEmpty()) {
            logger.debug("Phone extraction: content is null or empty");
            return null;
        }

        logger.info("Phone extraction - Document content preview (first 500 chars): '{}'",
                content.substring(0, Math.min(500, content.length())));

        if (content.contains("?1?555?234?7890")) {
            logger.info("Phone extraction - Found corrupted phone pattern '?1?555?234?7890'");
        }

        Matcher matcher = PHONE_PATTERN.matcher(content);

        if (matcher.find()) {
            String found = matcher.group();
            logger.info("Phone extraction - Regex found phone number: '{}'", found);

            String cleaned = cleanCorruptedPhoneNumber(found);
            logger.info("Phone extraction - Cleaned phone number: '{}'", cleaned);
            return cleaned;
        }

        logger.warn("Phone extraction - No phone number found using regex pattern");
        return null;
    }

    private String cleanCorruptedPhoneNumber(String phone) {
        if (phone == null) {
            return null;
        }

        // Replace corrupted characters and clean up
        String cleaned = phone
                .replaceAll("\\?", "") // Remove question marks
                .replaceAll("[^0-9+]", "") // Keep only digits and +
                .trim();

        // Add proper formatting if it looks like a US number
        if (cleaned.matches("1\\d{10}")) {
            cleaned = "+" + cleaned.charAt(0) + "-" +
                    cleaned.substring(1, 4) + "-" +
                    cleaned.substring(4, 7) + "-" +
                    cleaned.substring(7);
        }

        return cleaned;
    }

    /**
     * Extract name from content (attempts to find name from first few lines)
     * @param content Document content
     * @return Extracted name or null if not found
     */
    public String extractName(String content) {
        if (content == null || content.isEmpty()) {
            return null;
        }

        String[] lines = content.split("\n");
        for (int i = 0; i < Math.min(5, lines.length); i++) {
            String line = lines[i].trim();

            if (line.isEmpty() || line.toLowerCase().contains("curriculum") ||
                    line.toLowerCase().contains("resume") || line.toLowerCase().contains("cv")) {
                continue;
            }

            Matcher matcher = NAME_PATTERN.matcher(line);
            if (matcher.find()) {
                return matcher.group(1).trim();
            }

            if (line.matches("^[A-Za-z\\s]{3,50}$") && line.split("\\s").length >= 2) {
                return line.trim();
            }
        }

        return null;
    }

    /**
     * Check if file format is supported
     * @param fileName File name with extension
     * @return true if supported
     */
    public boolean isSupportedFormat(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return false;
        }

        String extension = getFileExtension(fileName).toLowerCase();
        return SUPPORTED_FORMATS.contains(extension);
    }

    /**
     * Get file extension from filename
     */
    private String getFileExtension(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return "";
        }

        int lastDotIndex = fileName.lastIndexOf('.');
        return lastDotIndex > 0 ? fileName.substring(lastDotIndex + 1) : "";
    }

    /**
     * Clean extracted content by removing extra whitespace and normalizing text
     */
    private String cleanContent(String content) {
        if (content == null) {
            return "";
        }

        return content
                .replaceAll("\\r\\n|\\r|\\n", "\n") // Normalize line breaks
                .replaceAll("\\s+", " ")           // Replace multiple spaces with single space
                .replaceAll("\\n\\s*\\n", "\n")    // Remove empty lines
                .trim();
    }

    /**
     * Clean and normalize phone number
     */
    private String cleanPhoneNumber(String phone) {
        if (phone == null) {
            return null;
        }

        return phone.replaceAll("[^0-9+]", "");
    }

    /**
     * Custom exception for document parsing errors
     */
//    public static class DocumentParsingException extends Exception {
//        public DocumentParsingException(String message) {
//            super(message);
//        }
//
//        public DocumentParsingException(String message, Throwable cause) {
//            super(message, cause);
//        }
//    }
}

