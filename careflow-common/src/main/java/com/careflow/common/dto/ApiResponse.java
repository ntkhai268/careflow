package com.careflow.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Builder
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {
    private int status;
    private String message;
    private T data;
    @Builder.Default
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant timestamp = Instant.now();

    public ApiResponse(int status, String message, T data, Instant timestamp) {
        setStatus(status);
        this.message = message;
        this.data = data;
        this.timestamp = timestamp == null ? Instant.now() : timestamp;
    }

    public void setStatus(int status) {
        if (status < 100 || status > 599) {
            throw new IllegalArgumentException("HTTP status must be between 100 and 599");
        }
        this.status = status;
    }

    public static <T> ApiResponse<T> success(T data) {
        return of(200, "Success", data);
    }

    public static <T> ApiResponse<T> success(String message, T data) {
        return of(200, message, data);
    }

    public static <T> ApiResponse<T> created(String message, T data) {
        return of(201, message, data);
    }

    public static <T> ApiResponse<T> of(int status, String message, T data) {
        return new ApiResponse<>(status, message, data, Instant.now());
    }

    public static <T> ApiResponse<T> error(int status, String message) {
        return error(status, message, null);
    }

    public static <T> ApiResponse<T> error(int status, String message, T details) {
        if (status < 400) {
            throw new IllegalArgumentException("Error response must use a 4xx or 5xx status");
        }
        return of(status, message, details);
    }
}
