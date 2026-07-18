package com.careflow.identity.dto;

import com.careflow.identity.domain.User;
import com.careflow.identity.domain.UserRole;
import com.careflow.identity.domain.UserStatus;

import java.time.Instant;
import java.util.UUID;

public record UserResponse(UUID id, String username, String email, UserRole role, UserStatus status,
                           Instant lastLoginAt) {
    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getUsername(), user.getEmail(), user.getRole(),
                user.getStatus(), user.getLastLoginAt());
    }
}
