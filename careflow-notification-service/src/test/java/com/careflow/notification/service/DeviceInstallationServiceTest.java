package com.careflow.notification.service;

import com.careflow.notification.domain.DeviceInstallation;
import com.careflow.notification.domain.DevicePlatform;
import com.careflow.notification.dto.DeviceInstallationResponse;
import com.careflow.notification.dto.RegisterDeviceRequest;
import com.careflow.notification.repository.DeviceInstallationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DeviceInstallationServiceTest {
    private DeviceInstallationRepository repository;
    private DeviceInstallationService service;

    @BeforeEach
    void setUp() {
        repository = mock(DeviceInstallationRepository.class);
        service = new DeviceInstallationService(repository);
        when(repository.save(any(DeviceInstallation.class))).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    void registersDeviceForAuthenticatedUser() {
        UUID userId = UUID.randomUUID();
        when(repository.findByUserIdAndDeviceId(userId, "phone-1")).thenReturn(Optional.empty());
        when(repository.findByRegistrationToken("token-1")).thenReturn(Optional.empty());

        DeviceInstallationResponse response = service.register(userId,
                new RegisterDeviceRequest("phone-1", "token-1", DevicePlatform.ANDROID, "1.0.0+1"));

        assertThat(response.deviceId()).isEqualTo("phone-1");
        assertThat(response.enabled()).isTrue();
        ArgumentCaptor<DeviceInstallation> captor = ArgumentCaptor.forClass(DeviceInstallation.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(userId);
        assertThat(captor.getValue().getRegistrationToken()).isEqualTo("token-1");
    }

    @Test
    void transfersRefreshedTokenWithoutDeletingDeliveryAudit() {
        UUID userId = UUID.randomUUID();
        DeviceInstallation oldOwner = installation(UUID.randomUUID(), UUID.randomUUID(), "old-phone", "token-1");
        when(repository.findByUserIdAndDeviceId(userId, "phone-1")).thenReturn(Optional.empty());
        when(repository.findByRegistrationToken("token-1")).thenReturn(Optional.of(oldOwner));

        service.register(userId,
                new RegisterDeviceRequest("phone-1", "token-1", DevicePlatform.ANDROID, null));

        assertThat(oldOwner.isEnabled()).isFalse();
        assertThat(oldOwner.getRegistrationToken()).startsWith("revoked:");
        verify(repository).flush();
        verify(repository, times(2)).save(any(DeviceInstallation.class));
        verify(repository, never()).delete(any());
    }

    @Test
    void unregisterIsIdempotent() {
        UUID userId = UUID.randomUUID();
        DeviceInstallation installation = installation(UUID.randomUUID(), userId, "phone-1", "token-1");
        when(repository.findByUserIdAndDeviceId(userId, "phone-1")).thenReturn(Optional.of(installation));

        service.unregister(userId, "phone-1");

        assertThat(installation.isEnabled()).isFalse();
        verify(repository).save(installation);
    }

    private DeviceInstallation installation(UUID id, UUID userId, String deviceId, String token) {
        DeviceInstallation result = new DeviceInstallation();
        result.setId(id);
        result.setUserId(userId);
        result.setDeviceId(deviceId);
        result.setRegistrationToken(token);
        result.setPlatform(DevicePlatform.ANDROID);
        result.setEnabled(true);
        result.setCreatedAt(Instant.now());
        result.setUpdatedAt(Instant.now());
        result.setLastSeenAt(Instant.now());
        return result;
    }
}
