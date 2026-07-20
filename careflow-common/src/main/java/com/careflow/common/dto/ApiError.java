package com.careflow.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ApiError(
        String code,
        String path,
        String correlationId,
        List<FieldViolation> violations) {

    public ApiError {
        violations = violations == null ? List.of() : List.copyOf(violations);
    }

    public static ApiError of(String code, String path, String correlationId) {
        return new ApiError(code, path, correlationId, List.of());
    }
}
