package com.careflow.queue.dto;

import com.careflow.queue.domain.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record QueueEntryResponse(
        UUID entryId, UUID appointmentId, UUID patientId, UUID userId, UUID departmentId,
        String departmentName, String roomCode, LocalDate queueDate, String queueNumber,
        PriorityLevel priorityLevel, QueueStatus queueStatus, Integer effectivePosition,
        Integer estimatedWaitMinutes, int callAttempts, int missedCount,
        UUID calledByUserId, Instant scheduledStartAt, Instant checkedInAt, Instant calledAt,
        Instant startedAt, Instant completedAt, Instant missedAt,
        QueueType type, ConsultationPhase consultationPhase, QueueClass queueClass,
        SchedulingLane schedulingLane, String servicePointId,
        UUID consultationId, UUID labOrderId, UUID prescriptionId) {

    public static QueueEntryResponse from(QueueEntry entry, QueueConfigResponse config,
                                          Integer position, Integer wait) {
        return build(entry, config.departmentName(), config.roomCode(), position, wait);
    }

    public static QueueEntryResponse fromServicePoint(QueueEntry entry, Integer position, Integer wait) {
        return build(entry, null, null, position, wait);
    }

    private static QueueEntryResponse build(QueueEntry entry, String departmentName, String roomCode,
                                            Integer position, Integer wait) {
        return new QueueEntryResponse(entry.getId(), entry.getAppointmentId(), entry.getPatientId(), entry.getUserId(),
                entry.getDepartmentId(), departmentName, roomCode, entry.getQueueDate(), entry.getQueueNumber(),
                entry.getPriorityLevel(), entry.getStatus(), position, wait,
                entry.getCallAttempts(), entry.getMissedCount(), entry.getCalledByUserId(), entry.getScheduledStartAt(),
                entry.getCheckedInAt(), entry.getCalledAt(), entry.getStartedAt(), entry.getCompletedAt(),
                entry.getMissedAt(), entry.getQueueType(), entry.getConsultationPhase(), entry.getQueueClass(),
                entry.getSchedulingLane(), entry.getServicePointId(), entry.getConsultationId(),
                entry.getLabOrderId(), entry.getPrescriptionId());
    }
}
