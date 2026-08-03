package com.careflow.notification.service;

import com.careflow.notification.domain.DeviceInstallation;
import com.careflow.notification.dto.DeviceInstallationResponse;
import com.careflow.notification.dto.RegisterDeviceRequest;
import com.careflow.notification.repository.DeviceInstallationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class DeviceInstallationService {
    private final DeviceInstallationRepository installations;

    public DeviceInstallationService(DeviceInstallationRepository installations) {
        this.installations = installations;
    }

    @Transactional
    public DeviceInstallationResponse register(UUID userId, RegisterDeviceRequest request) {
        Instant now = Instant.now();
        DeviceInstallation installation = installations.findByUserIdAndDeviceId(userId, request.deviceId())
                .orElseGet(() -> newInstallation(userId, request.deviceId(), now));

        installations.findByRegistrationToken(request.registrationToken())
                .filter(existing -> !existing.getId().equals(installation.getId()))
                .ifPresent(existing -> {
                    existing.setRegistrationToken("revoked:" + UUID.randomUUID());
                    existing.setEnabled(false);
                    existing.setUpdatedAt(now);
                    installations.save(existing);
                    installations.flush();
                });

        installation.setRegistrationToken(request.registrationToken());
        installation.setPlatform(request.platform());
        installation.setAppVersion(request.appVersion());
        installation.setEnabled(true);
        installation.setUpdatedAt(now);
        installation.setLastSeenAt(now);
        return toResponse(installations.save(installation));
    }

    @Transactional
    public void unregister(UUID userId, String deviceId) {
        installations.findByUserIdAndDeviceId(userId, deviceId).ifPresent(installation -> {
            installation.setEnabled(false);
            installation.setUpdatedAt(Instant.now());
            installations.save(installation);
        });
    }

    private DeviceInstallation newInstallation(UUID userId, String deviceId, Instant now) {
        DeviceInstallation installation = new DeviceInstallation();
        installation.setId(UUID.randomUUID());
        installation.setUserId(userId);
        installation.setDeviceId(deviceId);
        installation.setCreatedAt(now);
        return installation;
    }

    private DeviceInstallationResponse toResponse(DeviceInstallation installation) {
        return new DeviceInstallationResponse(installation.getId(), installation.getDeviceId(),
                installation.getPlatform(), installation.getAppVersion(), installation.isEnabled(),
                installation.getLastSeenAt());
    }
}
