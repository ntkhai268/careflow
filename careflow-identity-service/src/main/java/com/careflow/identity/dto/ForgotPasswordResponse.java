package com.careflow.identity.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ForgotPasswordResponse(String resetToken, Instant expiresAt) {
    public static ForgotPasswordResponse accepted() {
        return new ForgotPasswordResponse(null, null);
    }
}
