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
    @org.springframework.beans.factory.annotation.Autowired private org.springframework.jdbc.core.JdbcTemplate jdbc;

    public DeviceInstallationService(DeviceInstallationRepository installations) {
        this.installations = installations;
    }

    @Transactional
    public DeviceInstallationResponse register(UUID userId, UUID sessionId, RegisterDeviceRequest request) {
        if(userId==null || sessionId==null)throw new com.careflow.common.exception.BusinessException(401,"Cần phiên đã xác minh để đăng ký thông báo.");
        // Production bean requires JDBC; direct unit fixtures do not exercise PostgreSQL locks.
        if(jdbc!=null)for(String key:java.util.stream.Stream.of("push-token:"+request.registrationToken(),"push-device:"+userId+":"+request.deviceId()).sorted().toList())
            jdbc.queryForObject("SELECT pg_advisory_xact_lock(hashtextextended(?,0))",Object.class,key);
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

        if(!installation.isEnabled() || installation.getBindingId()==null || !request.registrationToken().equals(installation.getRegistrationToken()))installation.setBindingId(UUID.randomUUID());
        installation.setSessionId(sessionId);
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
                installation.getLastSeenAt(), installation.getBindingId());
    }
}
