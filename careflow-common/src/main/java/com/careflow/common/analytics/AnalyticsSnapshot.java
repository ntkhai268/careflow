package com.careflow.common.analytics;
import java.time.*;
import java.util.UUID;
/** Minimal operational facts. Patient, user, doctor and clinical fields are intentionally absent. */
public record AnalyticsSnapshot(Source source, UUID id, long revision, Instant updatedAt,
        LocalDate businessDate, UUID departmentId, String departmentCode, String roomId,
        String status, UUID appointmentId, UUID initialEntryId, UUID consultationId,
        String phase, Instant checkedInAt, Instant eligibleSinceAt, Instant startedAt,
        Instant completedAt, int missedCount) {
    public enum Source { APPOINTMENT, QUEUE, CONSULTATION }
    public AnalyticsSnapshot {
        if (source == null || id == null || updatedAt == null || businessDate == null ||
            revision < 0 || missedCount < 0 || status == null || status.length() > 30) {
            throw new IllegalArgumentException("Invalid analytics snapshot");
        }
    }
}
