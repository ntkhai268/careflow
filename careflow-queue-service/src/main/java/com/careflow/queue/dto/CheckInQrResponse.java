package com.careflow.queue.dto;

import java.time.Instant;
import java.time.LocalDate;

public record CheckInQrResponse(
        String siteId,
        String sessionCode,
        LocalDate sessionDate,
        String qrToken,
        Instant expiresAt) {
}
