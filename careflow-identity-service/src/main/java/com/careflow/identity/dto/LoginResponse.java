package com.careflow.identity.dto;

public record LoginResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresInSeconds,
        long refreshTokenExpiresInSeconds,
        boolean rememberMe,
        UserResponse user) {
}
