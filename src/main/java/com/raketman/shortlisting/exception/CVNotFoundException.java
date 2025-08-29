package com.raketman.shortlisting.exception;

public class CVNotFoundException extends RuntimeException {
    public CVNotFoundException(Long id) {
        super("CV with id " + id + " not found");
    }
}
