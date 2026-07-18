package com.careflow.notification.dto;

import java.time.Instant;
import java.util.UUID;

public record NotificationMessage(UUID eventId, String type, UUID entryId, UUID departmentId,
                                  String queueNumber, String roomCode, Integer estimatedWaitMinutes,
                                  Instant occurredAt) {
}
