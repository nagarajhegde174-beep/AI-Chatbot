package com.nexaai.user.exception;

import org.springframework.http.HttpStatus;

public class UserException extends RuntimeException {

    private final String code;
    private final HttpStatus httpStatus;

    public UserException(String code, String message, HttpStatus httpStatus) {
        super(message);
        this.code = code;
        this.httpStatus = httpStatus;
    }

    public String getCode() { return code; }
    public HttpStatus getHttpStatus() { return httpStatus; }
}
