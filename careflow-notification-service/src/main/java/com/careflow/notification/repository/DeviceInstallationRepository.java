package com.careflow.notification.repository;

import com.careflow.notification.domain.DeviceInstallation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DeviceInstallationRepository extends JpaRepository<DeviceInstallation, UUID> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select d from DeviceInstallation d where d.id=:id")
    Optional<DeviceInstallation> findLockedById(@org.springframework.data.repository.query.Param("id") UUID id);
    Optional<DeviceInstallation> findByUserIdAndDeviceId(UUID userId, String deviceId);
    Optional<DeviceInstallation> findByRegistrationToken(String registrationToken);
    List<DeviceInstallation> findByUserIdAndEnabledTrue(UUID userId);
}
