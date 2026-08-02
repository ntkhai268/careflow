package com.careflow.queue.repository;

import com.careflow.queue.domain.QueueNumberSequence;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface QueueNumberSequenceRepository extends JpaRepository<QueueNumberSequence, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<QueueNumberSequence> findByQueueConfigIdAndQueueDate(UUID configId, LocalDate date);
}
