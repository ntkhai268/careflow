package com.careflow.queue.repository;

import com.careflow.queue.domain.ServicePointSequence;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface ServicePointSequenceRepository extends JpaRepository<ServicePointSequence, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ServicePointSequence> findByServicePointIdAndQueueDate(String servicePointId, LocalDate queueDate);
}
