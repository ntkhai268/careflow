package com.careflow.notification.service;

import com.careflow.notification.config.FirebasePushProperties;
import com.careflow.notification.domain.*;
import com.careflow.notification.repository.DeviceInstallationRepository;
import com.careflow.notification.repository.NotificationRepository;
import com.careflow.notification.repository.PushDeliveryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PushDeliveryServiceTest {
    private FirebasePushProperties properties;
    private DeviceInstallationRepository installations;
    private PushDeliveryRepository deliveries;
    private NotificationRepository notifications;
    private PushSender sender;
    private UUID notificationOwner;
    private PushDeliveryService service;

    @BeforeEach
    void setUp() {
        properties = new FirebasePushProperties();
        properties.setEnabled(true);
        properties.setMaxAttempts(3);
        installations = mock(DeviceInstallationRepository.class);
        deliveries = mock(PushDeliveryRepository.class);
        notifications = mock(NotificationRepository.class);
        sender = mock(PushSender.class);
        service = new PushDeliveryService(properties, installations, deliveries, notifications, sender);
        var sessions=mock(PushSessionClient.class);when(sessions.active(any(),any())).thenReturn(true);
        var owners=mock(com.careflow.common.security.PatientOwnershipClient.class);
        when(owners.owner(any())).thenAnswer(i->notificationOwner);
        org.springframework.test.util.ReflectionTestUtils.setField(service,"sessions",sessions);
        org.springframework.test.util.ReflectionTestUtils.setField(service,"owners",owners);
        when(deliveries.save(any(PushDelivery.class))).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    void enqueuesOneDurableDeliveryPerActiveDevice() {
        Notification notification = notification();
        DeviceInstallation device = device(notification.getRecipientUserId());
        when(installations.findByUserIdAndEnabledTrue(notification.getRecipientUserId()))
                .thenReturn(List.of(device));

        service.enqueue(notification);

        ArgumentCaptor<PushDelivery> captor = ArgumentCaptor.forClass(PushDelivery.class);
        verify(deliveries).save(captor.capture());
        assertThat(captor.getValue().getNotificationId()).isEqualTo(notification.getId());
        assertThat(captor.getValue().getRegistrationToken()).isEqualTo("fcm-token");
        assertThat(captor.getValue().getStatus()).isEqualTo(PushDeliveryStatus.PENDING);
    }

    @Test
    void successfulSendMarksDeliverySent() {
        Notification notification = notification();
        DeviceInstallation device = device(notification.getRecipientUserId());
        PushDelivery delivery = delivery(notification, device);
        when(deliveries.findDueForUpdate())
                .thenReturn(List.of(delivery));
        when(notifications.findById(notification.getId())).thenReturn(Optional.of(notification));
        when(installations.findLockedById(device.getId())).thenReturn(Optional.of(device));

        service.dispatchDue();

        verify(sender).send("fcm-token", notification, device.getBindingId());
        assertThat(delivery.getStatus()).isEqualTo(PushDeliveryStatus.SENT);
        assertThat(delivery.getSentAt()).isNotNull();
    }

    @Test
    void permanentFcmFailureDisablesStaleToken() {
        Notification notification = notification();
        DeviceInstallation device = device(notification.getRecipientUserId());
        PushDelivery delivery = delivery(notification, device);
        when(deliveries.findDueForUpdate())
                .thenReturn(List.of(delivery));
        when(notifications.findById(notification.getId())).thenReturn(Optional.of(notification));
        when(installations.findLockedById(device.getId())).thenReturn(Optional.of(device));
        doThrow(new PushSendException("token is not registered", true, null))
                .when(sender).send(anyString(), any(), any());

        service.dispatchDue();

        assertThat(delivery.getStatus()).isEqualTo(PushDeliveryStatus.DEAD);
        assertThat(device.isEnabled()).isFalse();
        verify(installations).save(device);
    }

    @Test
    void transientFailureIsRetriedLater() {
        Notification notification = notification();
        DeviceInstallation device = device(notification.getRecipientUserId());
        PushDelivery delivery = delivery(notification, device);
        Instant before = Instant.now();
        when(deliveries.findDueForUpdate())
                .thenReturn(List.of(delivery));
        when(notifications.findById(notification.getId())).thenReturn(Optional.of(notification));
        when(installations.findLockedById(device.getId())).thenReturn(Optional.of(device));
        doThrow(new PushSendException("FCM unavailable", false, null))
                .when(sender).send(anyString(), any(), any());

        service.dispatchDue();

        assertThat(delivery.getStatus()).isEqualTo(PushDeliveryStatus.FAILED);
        assertThat(delivery.getAttempts()).isEqualTo(1);
        assertThat(delivery.getNextAttemptAt()).isAfter(before);
        assertThat(device.isEnabled()).isTrue();
    }

    private Notification notification() {
        Notification result = new Notification();
        result.setId(UUID.randomUUID());
        notificationOwner=UUID.randomUUID();result.setRecipientUserId(notificationOwner);result.setPatientId(UUID.randomUUID());
        result.setType(NotificationType.QUEUE_CALLED);
        result.setTitle("Đến lượt khám");
        result.setBody("Mời vào phòng khám");
        result.setCreatedAt(Instant.now());
        return result;
    }

    private DeviceInstallation device(UUID userId) {
        DeviceInstallation result = new DeviceInstallation();
        result.setId(UUID.randomUUID());
        result.setUserId(userId);
        result.setRegistrationToken("fcm-token");
        result.setBindingId(UUID.randomUUID());result.setSessionId(UUID.randomUUID());
        result.setEnabled(true);
        return result;
    }

    private PushDelivery delivery(Notification notification, DeviceInstallation device) {
        PushDelivery result = new PushDelivery();
        result.setId(UUID.randomUUID());
        result.setNotificationId(notification.getId());
        result.setDeviceInstallationId(device.getId());
        result.setRegistrationToken(device.getRegistrationToken());
        result.setBindingId(device.getBindingId());result.setExpiresAt(Instant.now().plusSeconds(300));
        result.setStatus(PushDeliveryStatus.PENDING);
        result.setCreatedAt(Instant.now());
        result.setNextAttemptAt(Instant.now());
        return result;
    }
}
