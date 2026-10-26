package com.nexaai.document.exception;

import org.springframework.http.HttpStatus;

public class DocumentException extends RuntimeException {

    private final String code;
    private final HttpStatus status;

    public DocumentException(String code, String message, HttpStatus status) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public String getCode() {
        return code;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
