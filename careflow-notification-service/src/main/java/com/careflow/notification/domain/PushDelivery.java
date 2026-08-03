package com.careflow.notification.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "push_deliveries", schema = "notification",
        uniqueConstraints = @UniqueConstraint(name = "uk_push_delivery_notification_device",
                columnNames = {"notification_id", "device_installation_id"}))
@Getter @Setter @NoArgsConstructor
public class PushDelivery {
    @Id
    private UUID id;
    @Column(name = "notification_id", nullable = false)
    private UUID notificationId;
    @Column(name = "device_installation_id", nullable = false)
    private UUID deviceInstallationId;
    @Column(name = "registration_token", nullable = false, length = 512)
    private String registrationToken;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PushDeliveryStatus status;
    @Column(nullable = false)
    private int attempts;
    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "sent_at")
    private Instant sentAt;
    @Column(name = "last_error")
    private String lastError;
}
