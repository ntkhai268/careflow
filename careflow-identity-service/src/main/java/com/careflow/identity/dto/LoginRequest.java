package com.careflow.identity.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(@NotBlank String usernameOrEmail, @NotBlank String password, String otp, String recoveryCode) {
    public LoginRequest(String usernameOrEmail, String password) { this(usernameOrEmail, password, null, null); }
}
