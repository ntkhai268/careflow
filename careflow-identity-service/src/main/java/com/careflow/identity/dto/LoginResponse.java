package com.careflow.identity.dto;

public record LoginResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresInSeconds,
        long refreshTokenExpiresInSeconds,
        UserResponse user) {
}
