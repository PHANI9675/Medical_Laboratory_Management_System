package com.lab_processing.lps.exception;

import java.time.LocalDateTime;

public class ApiErrorResponse {

    private String errorCode;
    private String message;
    private LocalDateTime timestamp;

    public ApiErrorResponse(String errorCode, String message) {
        this.errorCode = errorCode;
        this.message = message;
        this.timestamp = LocalDateTime.now();
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getMessage() {
        return message;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }
}