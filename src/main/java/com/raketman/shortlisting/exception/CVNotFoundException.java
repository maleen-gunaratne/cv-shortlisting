package com.raketman.shortlisting.exception;

public class CVNotFoundException extends RuntimeException {
    public CVNotFoundException(String id) {
        super("CV with id " + id + " not found");
    }
}
