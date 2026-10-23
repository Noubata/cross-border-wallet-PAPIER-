package com.example.crossborder.shared.error;

import org.springframework.http.HttpStatus;

public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String messageKey;

    public ApiException(HttpStatus status, String messageKey) {
        super(messageKey);
        this.status = status;
        this.messageKey = messageKey;
    }

    public HttpStatus status() {
        return status;
    }

    public String messageKey() {
        return messageKey;
    }
}