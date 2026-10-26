package com.nexaai.document.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(DocumentException.class)
    public ResponseEntity<Map<String, Object>> handleDocumentException(DocumentException ex) {
        return buildErrorResponse(ex.getCode(), ex.getMessage(), ex.getStatus(), null);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> handleMaxUploadSizeExceeded(MaxUploadSizeExceededException ex) {
        return buildErrorResponse("DOCUMENT_TOO_LARGE", "File size exceeds the 25 MB limit.", HttpStatus.PAYLOAD_TOO_LARGE, null);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
        return buildErrorResponse("INVALID_ARGUMENT", ex.getMessage(), HttpStatus.BAD_REQUEST, null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception ex) {
        return buildErrorResponse("INTERNAL_SERVER_ERROR", "An unexpected error occurred processing the document.", HttpStatus.INTERNAL_SERVER_ERROR, null);
    }

    private ResponseEntity<Map<String, Object>> buildErrorResponse(String code, String message, HttpStatus status, Object details) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("code", code);
        error.put("message", message);
        error.put("correlationId", UUID.randomUUID().toString());
        error.put("timestamp", Instant.now().toString());
        error.put("details", details);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", error);

        return ResponseEntity.status(status).body(body);
    }
}
