package com.careflow.notification.repository;

import com.careflow.notification.domain.DeviceInstallation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DeviceInstallationRepository extends JpaRepository<DeviceInstallation, UUID> {
    Optional<DeviceInstallation> findByUserIdAndDeviceId(UUID userId, String deviceId);
    Optional<DeviceInstallation> findByRegistrationToken(String registrationToken);
    List<DeviceInstallation> findByUserIdAndEnabledTrue(UUID userId);
}
