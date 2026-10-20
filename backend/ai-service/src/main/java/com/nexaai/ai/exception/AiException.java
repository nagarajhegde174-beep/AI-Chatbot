package com.nexaai.ai.exception;

/**
 * Base exception for classified AI provider errors.
 * Subclasses map 1:1 to the error codes in SERVICE_CONTRACTS.md §8.4.
 */
public class AiException extends RuntimeException {

    private final String errorCode;
    private final int httpStatus;
    private final boolean retryable;

    public AiException(String errorCode, int httpStatus, boolean retryable, String message) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
        this.retryable = retryable;
    }

    public AiException(String errorCode, int httpStatus, boolean retryable, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
        this.retryable = retryable;
    }

    public String getErrorCode() { return errorCode; }
    public int getHttpStatus() { return httpStatus; }
    public boolean isRetryable() { return retryable; }
}
