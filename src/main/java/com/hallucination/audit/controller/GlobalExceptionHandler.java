package com.hallucination.audit.controller;

import com.hallucination.audit.exception.AuditLogNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AuditLogNotFoundException.class)
    public ProblemDetail handleAuditLogNotFound(AuditLogNotFoundException exception) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
        problemDetail.setTitle("Audit log not found");
        return problemDetail;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidationErrors(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .findFirst()
                .orElse("Validation failed");

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, message);
        problemDetail.setTitle("Invalid audit request");
        return problemDetail;
    }

    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    public ProblemDetail handleMaxUploadSizeExceeded(org.springframework.web.multipart.MaxUploadSizeExceededException exception) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.PAYLOAD_TOO_LARGE,
                "The uploaded file exceeds the maximum size limit of 200MB. Please upload a compressed version or split the file."
        );
        problemDetail.setTitle("File Upload Size Limit Exceeded");
        return problemDetail;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpectedErrors(Exception exception) {
        exception.printStackTrace();
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                exception.getMessage()
        );
        problemDetail.setTitle("Audit pipeline failure");
        return problemDetail;
    }
}
