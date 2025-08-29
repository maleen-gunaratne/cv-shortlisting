package com.raketman.shortlisting.batch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.NonTransientResourceException;
import org.springframework.batch.item.ParseException;
import org.springframework.batch.item.UnexpectedInputException;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FilenameFilter;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;


public class CVItemReader implements ItemReader<File> {

    private static final Logger logger = LoggerFactory.getLogger(CVItemReader.class);

    private String inputDirectory;
    private List<File> files;
    private AtomicInteger currentIndex = new AtomicInteger(0);

    private static final String[] SUPPORTED_EXTENSIONS = {
            ".pdf", ".docx", ".doc", ".txt", ".rtf"
    };

    public void setInputDirectory(String inputDirectory) {
        this.inputDirectory = inputDirectory;
        initializeFiles();
    }

    private void initializeFiles() {
        if (inputDirectory == null || inputDirectory.isEmpty()) {
            throw new IllegalArgumentException("Input directory cannot be null or empty");
        }

        File directory = new File(inputDirectory);
        if (!directory.exists()) {
            throw new IllegalArgumentException("Input directory does not exist: " + inputDirectory);
        }

        if (!directory.isDirectory()) {
            throw new IllegalArgumentException("Input path is not a directory: " + inputDirectory);
        }

        // Filter files by supported extensions
        FilenameFilter filter = (dir, name) -> {
            String lowerCaseName = name.toLowerCase();
            return Arrays.stream(SUPPORTED_EXTENSIONS)
                    .anyMatch(lowerCaseName::endsWith);
        };

        File[] fileArray = directory.listFiles(filter);
        if (fileArray == null) {
            files = Arrays.asList();
            logger.warn("No files found in directory: {}", inputDirectory);
        } else {
            files = Arrays.asList(fileArray);
            logger.info("Found {} CV files to process in directory: {}", files.size(), inputDirectory);
        }
    }

    @Override
    public File read() throws Exception, UnexpectedInputException, ParseException, NonTransientResourceException {
        if (files == null) {
            return null;
        }

        int index = currentIndex.getAndIncrement();

        if (index < files.size()) {
            File file = files.get(index);
            logger.debug("Reading file: {} ({})", file.getName(), index + 1);
            return file;
        }

        // End of files reached
        logger.info("Finished reading all {} files", files.size());
        return null;
    }

    public int getTotalFiles() {
        return files != null ? files.size() : 0;
    }


    public void reset() {
        currentIndex.set(0);
    }

    public String getInputDirectory() {
        return inputDirectory;
    }
}
