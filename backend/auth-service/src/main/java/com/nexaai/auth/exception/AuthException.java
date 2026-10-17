package com.nexaai.auth.exception;

import org.springframework.http.HttpStatus;

public class AuthException extends RuntimeException {

    private final ErrorCode errorCode;
    private final HttpStatus httpStatus;
    private final Object details;

    public AuthException(ErrorCode errorCode, String message, HttpStatus httpStatus) {
        this(errorCode, message, httpStatus, null);
    }

    public AuthException(ErrorCode errorCode, String message, HttpStatus httpStatus, Object details) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
        this.details = details;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    public Object getDetails() {
        return details;
    }
}
