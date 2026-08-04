package com.careflow.queue.dto;

import java.time.LocalDate;
import java.util.List;

public record ServicePointQueueResponse(
        String servicePointId,
        LocalDate queueDate,
        List<QueueEntryResponse> entries,
        QueueEntryResponse recommendedNext) {
}
