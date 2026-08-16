package com.careflow.queue.dto;

import com.careflow.queue.domain.QueueClass;

import java.util.UUID;

public record CheckInRequest(
        String qrToken,
        String ticketCode,
        String roomId,
        QueueClass queueClass,
        String priorityReasonCode,
        UUID appointmentId,
        String checkInQrToken,
        Double latitude,
        Double longitude,
        Double accuracyMeters) {

    public CheckInRequest(String qrToken, String roomId, QueueClass queueClass, String priorityReasonCode) {
        this(qrToken, null, roomId, queueClass, priorityReasonCode,
                null, null, null, null, null);
    }

    public CheckInRequest(String qrToken, String ticketCode, String roomId,
                          QueueClass queueClass, String priorityReasonCode) {
        this(qrToken, ticketCode, roomId, queueClass, priorityReasonCode,
                null, null, null, null, null);
    }
}
