package com.careflow.notification.repository;

import com.careflow.notification.domain.Notification;
import com.careflow.notification.domain.NotificationStatus;
import com.careflow.notification.domain.NotificationType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {
    boolean existsBySourceEventIdAndRecipientUserIdAndType(UUID sourceEventId, UUID recipientUserId,
                                                            NotificationType type);
    List<Notification> findByRecipientUserIdOrderByCreatedAtDesc(UUID recipientUserId, Pageable pageable);
    List<Notification> findByRecipientUserIdAndStatusOrderByCreatedAtDesc(UUID recipientUserId,
                                                                          NotificationStatus status,
                                                                          Pageable pageable);
    List<Notification> findByRecipientUserIdAndStatusOrderByCreatedAtAsc(UUID recipientUserId,
                                                                         NotificationStatus status);
    Optional<Notification> findByIdAndRecipientUserId(UUID id, UUID recipientUserId);
    long countByRecipientUserIdAndStatus(UUID recipientUserId, NotificationStatus status);

}
