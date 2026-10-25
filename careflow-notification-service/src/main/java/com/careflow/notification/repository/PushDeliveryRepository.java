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
    @org.springframework.data.jpa.repository.Query(value="SELECT * FROM notification.push_deliveries WHERE status IN ('PENDING','FAILED') AND next_attempt_at<=now() ORDER BY created_at,id LIMIT 50 FOR UPDATE SKIP LOCKED",nativeQuery=true)
    List<PushDelivery> findDueForUpdate();
    boolean existsByNotificationIdAndDeviceInstallationId(UUID notificationId, UUID deviceInstallationId);

    List<PushDelivery> findByStatusInAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
            Collection<PushDeliveryStatus> statuses, Instant dueAt, Pageable pageable);
}
