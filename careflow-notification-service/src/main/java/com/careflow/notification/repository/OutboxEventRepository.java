package com.careflow.notification.repository;

import com.careflow.notification.domain.OutboxEvent;
import com.careflow.notification.domain.OutboxStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {
    List<OutboxEvent> findByStatusInAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
            Set<OutboxStatus> statuses, Instant nextAttemptAt, Pageable pageable);
}
