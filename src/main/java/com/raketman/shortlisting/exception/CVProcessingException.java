package com.raketman.shortlisting.exception;


import lombok.Getter;

@Getter
public class CVProcessingException extends RuntimeException {
    private  String fileName;

    public CVProcessingException(String fileName, String message, Throwable cause) {
        super(message, cause);
        this.fileName = fileName;
    }

    public CVProcessingException(String asyncBatchProcessingFailed, Exception e) {

    }
}
