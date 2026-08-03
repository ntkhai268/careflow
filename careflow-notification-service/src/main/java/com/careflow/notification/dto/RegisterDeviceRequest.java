package com.careflow.notification.dto;

import com.careflow.notification.domain.DevicePlatform;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RegisterDeviceRequest(
        @NotBlank @Size(max = 160) String deviceId,
        @NotBlank @Size(max = 512) String registrationToken,
        @NotNull DevicePlatform platform,
        @Size(max = 40) String appVersion
) {}
