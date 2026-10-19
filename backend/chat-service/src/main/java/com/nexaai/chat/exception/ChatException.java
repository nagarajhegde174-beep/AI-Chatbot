package com.nexaai.chat.exception;

import org.springframework.http.HttpStatus;

public class ChatException extends RuntimeException {

    private final String errorCode;
    private final HttpStatus status;

    public ChatException(String errorCode, String message, HttpStatus status) {
        super(message);
        this.errorCode = errorCode;
        this.status = status;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
