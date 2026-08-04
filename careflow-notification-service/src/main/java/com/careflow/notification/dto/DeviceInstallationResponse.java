package com.careflow.notification.dto;

import com.careflow.notification.domain.DevicePlatform;

import java.time.Instant;
import java.util.UUID;

public record DeviceInstallationResponse(
        UUID id,
        String deviceId,
        DevicePlatform platform,
        String appVersion,
        boolean enabled,
        Instant lastSeenAt
) {}
