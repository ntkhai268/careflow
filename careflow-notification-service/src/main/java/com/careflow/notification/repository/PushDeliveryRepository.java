package com.careflow.notification.repository;

import com.careflow.notification.domain.PushDelivery;
import com.careflow.notification.domain.PushDeliveryStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface PushDeliveryRepository extends JpaRepository<PushDelivery, UUID> {
    boolean existsByNotificationIdAndDeviceInstallationId(UUID notificationId, UUID deviceInstallationId);

    List<PushDelivery> findByStatusInAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
            Collection<PushDeliveryStatus> statuses, Instant dueAt, Pageable pageable);
}
