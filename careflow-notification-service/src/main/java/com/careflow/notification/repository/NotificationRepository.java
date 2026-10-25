package com.careflow.notification.repository;

import com.careflow.notification.domain.Notification;
import com.careflow.notification.domain.NotificationStatus;
import com.careflow.notification.domain.NotificationType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.time.Instant;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {
    boolean existsBySourceEventIdAndRecipientUserIdAndType(UUID sourceEventId, UUID recipientUserId,
                                                            NotificationType type);
    @Query("select n from Notification n where n.recipientUserId = :recipientUserId order by n.createdAt desc, n.id desc")
    List<Notification> findByRecipientUserIdOrderByCreatedAtDesc(UUID recipientUserId, Pageable pageable);
    List<Notification> findByRecipientUserIdAndStatusOrderByCreatedAtDesc(UUID recipientUserId,
                                                                          NotificationStatus status,
                                                                          Pageable pageable);
    List<Notification> findByRecipientUserIdAndStatusOrderByCreatedAtAsc(UUID recipientUserId,
                                                                         NotificationStatus status);
    Optional<Notification> findByIdAndRecipientUserId(UUID id, UUID recipientUserId);
    long countByRecipientUserIdAndStatus(UUID recipientUserId, NotificationStatus status);

    @Query("select n from Notification n where n.recipientUserId = :user and (n.createdAt < :created or (n.createdAt = :created and n.id < :id)) order by n.createdAt desc, n.id desc")
    List<Notification> findOlder(UUID user, Instant created, UUID id, Pageable pageable);
}
