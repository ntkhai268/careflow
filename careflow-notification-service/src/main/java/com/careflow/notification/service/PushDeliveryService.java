package com.careflow.notification.service;

import com.careflow.common.security.PatientOwnershipClient;
import com.careflow.notification.config.FirebasePushProperties;
import com.careflow.notification.domain.DeviceInstallation;
import com.careflow.notification.domain.Notification;
import com.careflow.notification.domain.PushDelivery;
import com.careflow.notification.domain.PushDeliveryStatus;
import com.careflow.notification.repository.DeviceInstallationRepository;
import com.careflow.notification.repository.NotificationRepository;
import com.careflow.notification.repository.PushDeliveryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class PushDeliveryService {
    private static final Duration PUSH_EXPIRATION = Duration.ofMinutes(5);

    private final FirebasePushProperties properties;
    private final DeviceInstallationRepository installations;
    private final PushDeliveryRepository deliveries;
    private final NotificationRepository notifications;
    private final PushSender sender;

    @Autowired
    private PushSessionClient sessions;

    @Autowired
    private PatientOwnershipClient owners;

    public PushDeliveryService(FirebasePushProperties properties,
                               DeviceInstallationRepository installations,
                               PushDeliveryRepository deliveries,
                               NotificationRepository notifications,
                               PushSender sender) {
        this.properties = properties;
        this.installations = installations;
        this.deliveries = deliveries;
        this.notifications = notifications;
        this.sender = sender;
    }

    @Transactional
    public void enqueue(Notification notification) {
        if (!properties.isEnabled()) {
            return;
        }
        Instant now = Instant.now();
        for (DeviceInstallation installation : installations.findByUserIdAndEnabledTrue(
                notification.getRecipientUserId())) {
            if (deliveries.existsByNotificationIdAndDeviceInstallationId(
                    notification.getId(), installation.getId())) {
                continue;
            }
            PushDelivery delivery = new PushDelivery();
            delivery.setId(UUID.randomUUID());
            delivery.setNotificationId(notification.getId());
            delivery.setDeviceInstallationId(installation.getId());
            delivery.setRegistrationToken(installation.getRegistrationToken());
            delivery.setBindingId(installation.getBindingId());
            delivery.setExpiresAt(notification.getCreatedAt().plus(PUSH_EXPIRATION));
            delivery.setStatus(PushDeliveryStatus.PENDING);
            delivery.setNextAttemptAt(now);
            delivery.setCreatedAt(now);
            deliveries.save(delivery);
        }
    }

    @Scheduled(fixedDelayString = "${notification.push.firebase.poll-ms:1000}")
    @Transactional
    public void dispatchDue() {
        if (!properties.isEnabled()) {
            return;
        }
        List<PushDelivery> due = deliveries.findDueForUpdate();
        for (PushDelivery delivery : due) {
            dispatch(delivery);
        }
    }

    private void dispatch(PushDelivery delivery) {
        Notification notification = notifications.findById(delivery.getNotificationId()).orElse(null);
        DeviceInstallation installation = installations.findLockedById(delivery.getDeviceInstallationId()).orElse(null);
        if (notification == null || installation == null || !installation.isEnabled()) {
            dead(delivery, "Notification or active device installation no longer exists");
            return;
        }
        if (delivery.getExpiresAt() == null || !delivery.getExpiresAt().isAfter(Instant.now())) {
            dead(delivery, "PUSH_EXPIRED");
            return;
        }
        if (installation.getBindingId() == null
                || !installation.getBindingId().equals(delivery.getBindingId())
                || !notification.getRecipientUserId().equals(installation.getUserId())
                || !installation.getRegistrationToken().equals(delivery.getRegistrationToken())) {
            dead(delivery, "BINDING_CHANGED");
            return;
        }
        try {
            if (!sessions.active(installation.getUserId(), installation.getSessionId())) {
                installation.setEnabled(false);
                installations.save(installation);
                dead(delivery, "SESSION_REVOKED");
                return;
            }
            if (notification.getPatientId() == null
                    || !notification.getRecipientUserId().equals(owners.owner(notification.getPatientId()))) {
                dead(delivery, "PATIENT_OWNERSHIP_CHANGED");
                return;
            }
            sender.send(delivery.getRegistrationToken(), notification, installation.getBindingId());
            delivery.markSent(Instant.now());
        } catch (PushSendException exception) {
            delivery.setAttempts(delivery.getAttempts() + 1);
            delivery.setLastError("PUSH_DEPENDENCY_UNAVAILABLE");
            if (exception.isPermanent() || delivery.getAttempts() >= properties.getMaxAttempts()) {
                delivery.setStatus(PushDeliveryStatus.DEAD);
                if (exception.isPermanent()) {
                    installation.disable(Instant.now());
                    installations.save(installation);
                }
            } else {
                delivery.setStatus(PushDeliveryStatus.FAILED);
                long delayMinutes = Math.min(60, 1L << Math.min(delivery.getAttempts() - 1, 6));
                delivery.setNextAttemptAt(Instant.now().plus(Duration.ofMinutes(delayMinutes)));
            }
        } catch (RuntimeException exception) {
            delivery.setAttempts(delivery.getAttempts() + 1);
            delivery.setLastError("PUSH_DEPENDENCY_UNAVAILABLE");
            delivery.setStatus(delivery.getAttempts() >= properties.getMaxAttempts()
                    ? PushDeliveryStatus.DEAD
                    : PushDeliveryStatus.FAILED);
            delivery.setNextAttemptAt(Instant.now().plus(Duration.ofMinutes(1)));
        }
        deliveries.save(delivery);
    }

    private void dead(PushDelivery delivery, String reason) {
        delivery.markDead(reason);
        deliveries.save(delivery);
    }
}
