package com.careflow.notification.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notifications", schema = "notification",
        uniqueConstraints = @UniqueConstraint(name = "uk_notification_source_recipient_type",
                columnNames = {"source_event_id", "recipient_user_id", "notification_type"}))
@Getter @Setter @NoArgsConstructor
public class Notification {
    @Id
    private UUID id;
    @Column(name = "recipient_user_id", nullable = false)
    private UUID recipientUserId;
    @Column(name = "source_event_id", nullable = false)
    private UUID sourceEventId;
    @Column(name = "source_event_type", nullable = false, length = 100)
    private String sourceEventType;
    @Enumerated(EnumType.STRING)
    @Column(name = "notification_type", nullable = false, length = 50)
    private NotificationType type;
    @Column(nullable = false, length = 160)
    private String title;
    @Column(nullable = false, length = 500)
    private String body;
    @Column(name = "action_type", length = 50)
    private String actionType;
    @Column(name = "resource_id", length = 100)
    private String resourceId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationStatus status;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "read_at")
    private Instant readAt;
}
