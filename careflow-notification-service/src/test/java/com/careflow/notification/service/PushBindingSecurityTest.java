package com.careflow.notification.service;
import com.careflow.notification.config.FirebasePushProperties;
import com.careflow.notification.domain.*;
import com.careflow.notification.repository.*;
import com.careflow.common.security.PatientOwnershipClient;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class PushBindingSecurityTest {
    final DeviceInstallationRepository devices=mock(DeviceInstallationRepository.class);final PushDeliveryRepository deliveries=mock(PushDeliveryRepository.class);final NotificationRepository notifications=mock(NotificationRepository.class);final PushSender sender=mock(PushSender.class);final PushSessionClient sessions=mock(PushSessionClient.class);final PatientOwnershipClient owners=mock(PatientOwnershipClient.class);
    PushDeliveryService service;Notification n;DeviceInstallation device;PushDelivery delivery;
    @BeforeEach void setup(){var p=new FirebasePushProperties();p.setEnabled(true);p.setMaxAttempts(3);service=new PushDeliveryService(p,devices,deliveries,notifications,sender);ReflectionTestUtils.setField(service,"sessions",sessions);ReflectionTestUtils.setField(service,"owners",owners);
      n=new Notification();n.setId(UUID.randomUUID());n.setRecipientUserId(UUID.randomUUID());n.setPatientId(UUID.randomUUID());n.setCreatedAt(Instant.now());
      device=new DeviceInstallation();device.setId(UUID.randomUUID());device.setUserId(n.getRecipientUserId());device.setSessionId(UUID.randomUUID());device.setBindingId(UUID.randomUUID());device.setRegistrationToken("SYNTHETIC_TOKEN");device.setEnabled(true);
      delivery=new PushDelivery();delivery.setId(UUID.randomUUID());delivery.setNotificationId(n.getId());delivery.setDeviceInstallationId(device.getId());delivery.setRegistrationToken(device.getRegistrationToken());delivery.setBindingId(device.getBindingId());delivery.setExpiresAt(Instant.now().plusSeconds(300));delivery.setStatus(PushDeliveryStatus.PENDING);
      when(deliveries.findDueForUpdate()).thenReturn(List.of(delivery));when(notifications.findById(n.getId())).thenReturn(Optional.of(n));when(devices.findLockedById(device.getId())).thenReturn(Optional.of(device));when(sessions.active(any(),any())).thenReturn(true);when(owners.owner(n.getPatientId())).thenReturn(n.getRecipientUserId());
    }
    @Test void transferredTokenNeverReceivesPriorAccountsQueuedMessage(){device.setUserId(UUID.randomUUID());service.dispatchDue();verifyNoInteractions(sender);assertThat(delivery.getLastError()).isEqualTo("BINDING_CHANGED");}
    @Test void tokenRotationDropsOldSnapshot(){device.setRegistrationToken("NEW_TOKEN");service.dispatchDue();verifyNoInteractions(sender);assertThat(delivery.getStatus()).isEqualTo(PushDeliveryStatus.DEAD);}
    @Test void rebindDropsOldGeneration(){device.setBindingId(UUID.randomUUID());service.dispatchDue();verifyNoInteractions(sender);}
    @Test void revokedSessionDisablesRegistration(){when(sessions.active(any(),any())).thenReturn(false);service.dispatchDue();verifyNoInteractions(sender);assertThat(device.isEnabled()).isFalse();}
    @Test void ownershipRevokedAfterEnqueueBlocksPush(){when(owners.owner(n.getPatientId())).thenReturn(UUID.randomUUID());service.dispatchDue();verifyNoInteractions(sender);assertThat(delivery.getLastError()).isEqualTo("PATIENT_OWNERSHIP_CHANGED");}
    @Test void expiredAlertIsNotReplayedAfterLongOutage(){delivery.setExpiresAt(Instant.now().minusSeconds(1));service.dispatchDue();verifyNoInteractions(sender);assertThat(delivery.getLastError()).isEqualTo("PUSH_EXPIRED");}
    @Test void identityOutageRetriesWithoutDisablingTheToken(){when(sessions.active(any(),any())).thenThrow(new IllegalStateException("sensitive provider detail"));service.dispatchDue();verifyNoInteractions(sender);assertThat(device.isEnabled()).isTrue();assertThat(delivery.getStatus()).isEqualTo(PushDeliveryStatus.FAILED);assertThat(delivery.getLastError()).doesNotContain("sensitive");}
}
