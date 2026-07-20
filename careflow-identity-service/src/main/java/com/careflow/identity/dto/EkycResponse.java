package com.careflow.identity.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record EkycResponse(
        UUID verificationId,
        UUID userId,
        String documentNumber,
        String fullName,
        LocalDate dateOfBirth,
        double confidence,
        String status,
        boolean mock,
        Instant processedAt) {
}
