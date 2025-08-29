package com.raketman.shortlisting.util;

import com.raketman.shortlisting.dto.ServiceResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;

public class ResponseBuilder {

    public static <T> ResponseEntity<ServiceResponse<T>> success(
            String message, T data) {
        ServiceResponse<T> response = new ServiceResponse<>(
                true,
                        message,
                null,
                    null,
                         LocalDateTime.now(),
                         data
        );
        return ResponseEntity.ok(response);
    }

    public static <T> ResponseEntity<ServiceResponse<T>> error(
            String message,
            String errorCode,
            HttpStatus status,
            String path
    ) {
        return ResponseEntity.status(status).body(
                new ServiceResponse<>(
                        false,
                        message,
                        errorCode,
                        path,
                        LocalDateTime.now(),
                        null
                )
        );
    }
}

