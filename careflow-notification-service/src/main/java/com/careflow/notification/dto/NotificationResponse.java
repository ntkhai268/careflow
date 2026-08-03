package com.careflow.notification.dto;

import com.careflow.notification.domain.NotificationStatus;
import com.careflow.notification.domain.NotificationType;

import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(UUID id, NotificationType type, String title, String body,
                                   NotificationActionResponse action, NotificationStatus status,
                                   Instant createdAt, Instant readAt) {
}
