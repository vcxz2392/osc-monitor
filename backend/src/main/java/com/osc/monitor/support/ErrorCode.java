package com.osc.monitor.support;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

    INVALID_PARAMETER(HttpStatus.BAD_REQUEST, "요청 파라미터가 올바르지 않습니다."),
    INVALID_CURSOR(HttpStatus.BAD_REQUEST, "잘못된 커서입니다.");

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public HttpStatus status() {
        return status;
    }

    public String message() {
        return message;
    }
}
