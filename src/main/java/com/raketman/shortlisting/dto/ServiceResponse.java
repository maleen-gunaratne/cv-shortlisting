package com.raketman.shortlisting.dto;


import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class ServiceResponse<T> {
    private boolean success;
    private String message;
    private String errorCode;
    private String path;
    private LocalDateTime timestamp;
    private T data;

    // Generic constructor for normal success responses
    public ServiceResponse(boolean success, String message, T data) {
        this.success = success;
        this.message = message;
        this.data = data;
        this.timestamp = LocalDateTime.now();
    }

    // Factory method for errors
    public static <T> ServiceResponse<T> error(String message, String errorCode, String path) {
        return new ServiceResponse<>(
                false,
                message,
                errorCode,
                path,
                LocalDateTime.now(),
                null
        );
    }

    // ✅ Factory method for success
    public static <T> ServiceResponse<T> success(String message, T data) {
        return new ServiceResponse<>(
                true,
                message,
                null,
                null,
                LocalDateTime.now(),
                data
        );
    }
}

