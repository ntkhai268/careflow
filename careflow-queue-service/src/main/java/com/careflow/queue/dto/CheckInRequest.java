package com.careflow.queue.dto;

import jakarta.validation.constraints.NotBlank;
import com.careflow.queue.domain.QueueClass;

public record CheckInRequest(
        @NotBlank String qrToken,
        @NotBlank String roomId,
        QueueClass queueClass,
        String priorityReasonCode) {
}
