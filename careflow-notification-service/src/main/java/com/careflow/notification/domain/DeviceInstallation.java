package com.careflow.notification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "device_installations", schema = "notification",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_device_installation_user_device", columnNames = {"user_id", "device_id"}),
                @UniqueConstraint(name = "uk_device_installation_token", columnNames = "registration_token")
        })
@Getter
@Setter
@NoArgsConstructor
public class DeviceInstallation {
    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "session_id")
    private UUID sessionId;

    @Column(name = "binding_id")
    private UUID bindingId;

    @Version
    private long version;

    @Column(name = "device_id", nullable = false, length = 160)
    private String deviceId;

    @Column(name = "registration_token", nullable = false, length = 512)
    private String registrationToken;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DevicePlatform platform;

    @Column(name = "app_version", length = 40)
    private String appVersion;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    public void disable(Instant now) {
        enabled = false;
        updatedAt = now;
    }

    public void revokeToken(Instant now) {
        registrationToken = "revoked:" + UUID.randomUUID();
        disable(now);
    }
}
