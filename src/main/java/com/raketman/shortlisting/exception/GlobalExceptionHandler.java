package com.raketman.shortlisting.exception;

//import com.example.cvshortlisting.dto.ServiceResponse;
//import com.example.cvshortlisting.util.ResponseBuilder;
import com.raketman.shortlisting.dto.ServiceResponse;
import com.raketman.shortlisting.util.ResponseBuilder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ServiceResponse<Object>> handleConstraintViolation(
            ConstraintViolationException ex, HttpServletRequest request) {

        return ResponseBuilder.error(
                "Validation error: " + ex.getMessage(),
                "VALIDATION_ERROR",
                HttpStatus.BAD_REQUEST,
                request.getRequestURI()
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ServiceResponse<Object>> handleValidationErrors(
            MethodArgumentNotValidException ex, HttpServletRequest request) {

        String errorMessage = ex.getBindingResult().getAllErrors().get(0).getDefaultMessage();

        return ResponseBuilder.error(
                "Validation error: " + errorMessage,
                "VALIDATION_ERROR",
                HttpStatus.BAD_REQUEST,
                request.getRequestURI()
        );
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ServiceResponse<Object>> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex, HttpServletRequest request) {

        return ResponseBuilder.error(
                "Invalid parameter: " + ex.getName(),
                "TYPE_MISMATCH",
                HttpStatus.BAD_REQUEST,
                request.getRequestURI()
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ServiceResponse<Object>> handleGeneralException(
            Exception ex, HttpServletRequest request) {

        return ResponseBuilder.error(
                "Internal server error",
                "INTERNAL_ERROR",
                HttpStatus.INTERNAL_SERVER_ERROR,
                request.getRequestURI()
        );
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ServiceResponse<Object>> handleNotFound(
            ResourceNotFoundException ex, HttpServletRequest request) {

        return ResponseBuilder.error(
                ex.getMessage(),
                "NOT_FOUND",
                HttpStatus.NOT_FOUND,
                request.getRequestURI()
        );
    }

    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ServiceResponse<Object>> handleDuplicate(
            DuplicateResourceException ex, HttpServletRequest request) {

        return ResponseBuilder.error(
                ex.getMessage(),
                "DUPLICATE_RESOURCE",
                HttpStatus.CONFLICT,
                request.getRequestURI()
        );
    }

    @ExceptionHandler(CVNotFoundException.class)
    public ResponseEntity<ServiceResponse<Object>> handleCVNotFound(
            CVNotFoundException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                ServiceResponse.error(ex.getMessage(), "CV_NOT_FOUND", request.getRequestURI())
        );
    }

    @ExceptionHandler(CVProcessingException.class)
    public ResponseEntity<ServiceResponse<Object>> handleCVProcessingException(
            CVProcessingException ex, HttpServletRequest request) {


        String message = String.format("Failed to process CV '%s'", ex.getFileName());
        return ResponseBuilder.error(
                message,
                "CV_PROCESSING_ERROR",
                HttpStatus.INTERNAL_SERVER_ERROR,
                request.getRequestURI()
        );
    }

    @ExceptionHandler(DocumentParsingException.class)
    public ResponseEntity<ServiceResponse<Object>> handleDocumentParsing(
            DocumentParsingException ex, HttpServletRequest request) {
        return ResponseBuilder.error(
                ex.getMessage(),
                "DOCUMENT_PARSING_ERROR",
                HttpStatus.BAD_REQUEST,
                request.getRequestURI()
        );
    }
}


