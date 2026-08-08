package com.careflow.queue.dto;

import jakarta.validation.constraints.NotBlank;
import com.careflow.queue.domain.QueueClass;

public record CheckInRequest(
        String qrToken,
        String ticketCode,
        @NotBlank String roomId,
        QueueClass queueClass,
        String priorityReasonCode) {

    public CheckInRequest(String qrToken, String roomId, QueueClass queueClass, String priorityReasonCode) {
        this(qrToken, null, roomId, queueClass, priorityReasonCode);
    }
}
