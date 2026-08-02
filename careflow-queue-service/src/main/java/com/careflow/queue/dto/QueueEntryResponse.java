package com.careflow.queue.dto;

import com.careflow.queue.domain.PriorityLevel;
import com.careflow.queue.domain.QueueEntry;
import com.careflow.queue.domain.QueueStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record QueueEntryResponse(
        UUID entryId, UUID appointmentId, UUID patientId, UUID userId, UUID departmentId,
        String departmentName, String roomCode, LocalDate queueDate, String queueNumber,
        PriorityLevel priorityLevel, QueueStatus queueStatus, Integer effectivePosition,
        Integer estimatedWaitMinutes, int callAttempts, int missedCount,
        UUID calledByUserId, Instant scheduledStartAt, Instant checkedInAt, Instant calledAt,
        Instant startedAt, Instant completedAt,
        Instant missedAt) {
    public static QueueEntryResponse from(QueueEntry entry, QueueConfigResponse config,
                                          Integer position, Integer wait) {
        return new QueueEntryResponse(entry.getId(), entry.getAppointmentId(), entry.getPatientId(), entry.getUserId(),
                entry.getDepartmentId(), config.departmentName(), config.roomCode(), entry.getQueueDate(),
                entry.getQueueNumber(), entry.getPriorityLevel(), entry.getStatus(), position, wait,
                entry.getCallAttempts(), entry.getMissedCount(), entry.getCalledByUserId(), entry.getScheduledStartAt(),
                entry.getCheckedInAt(), entry.getCalledAt(),
                entry.getStartedAt(), entry.getCompletedAt(), entry.getMissedAt());
    }
}
