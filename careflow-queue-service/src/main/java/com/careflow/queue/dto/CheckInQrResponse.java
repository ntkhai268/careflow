package com.careflow.queue.dto;

import java.time.Instant;
import java.time.LocalDate;

public record CheckInQrResponse(
        String siteId,
        String roomId,
        String sessionCode,
        LocalDate sessionDate,
        String qrToken,
        Instant expiresAt) {
}
