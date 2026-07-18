package com.careflow.identity.dto;

public record LoginResponse(String accessToken, String tokenType, long expiresInSeconds, UserResponse user) {
}
