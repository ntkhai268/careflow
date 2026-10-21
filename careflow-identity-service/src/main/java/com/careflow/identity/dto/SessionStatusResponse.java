package com.careflow.identity.dto;

import com.careflow.identity.domain.UserRole;
import java.time.Instant;
import java.util.UUID;

public record SessionStatusResponse(boolean active, UUID userId, UserRole role, UUID sessionId, Instant expiresAt) {}
