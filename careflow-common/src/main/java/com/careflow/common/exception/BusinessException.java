package com.careflow.common.exception;

import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {
    private final int status;
    private final String code;

    public BusinessException(int status, String message) {
        this(status, "BUSINESS_ERROR", message);
    }

    public BusinessException(int status, String code, String message) {
        super(message);
        if (status < 400 || status > 599) {
            throw new IllegalArgumentException("BusinessException status must be between 400 and 599");
        }
        this.status = status;
        this.code = code == null || code.isBlank() ? "BUSINESS_ERROR" : code;
    }
}
