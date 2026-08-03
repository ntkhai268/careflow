package com.careflow.queue.dto;

import com.careflow.queue.domain.SchedulingLane;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record QueueDashboardResponse(
        UUID departmentId,
        String departmentName,
        String roomCode,
        LocalDate queueDate,
        List<QueueEntryResponse> entries,
        List<QueueEntryResponse> priorityQueue,
        List<QueueEntryResponse> normalQueue,
        List<QueueEntryResponse> resultReviewQueue,
        QueueEntryResponse recommendedNext,
        SchedulingLane lastServedLane,
        long schedulerVersion) {
}
