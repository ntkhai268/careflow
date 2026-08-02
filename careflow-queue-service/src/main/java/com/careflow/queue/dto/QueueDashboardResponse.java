package com.careflow.queue.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record QueueDashboardResponse(UUID departmentId, String departmentName, String roomCode,
                                     LocalDate queueDate, List<QueueEntryResponse> entries,
                                     QueueEntryResponse recommendedNext) {
}
