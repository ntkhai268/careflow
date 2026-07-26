package com.careflow.queue.repository;

import com.careflow.queue.domain.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface QueueEntryRepository extends JpaRepository<QueueEntry, UUID> {
    Optional<QueueEntry> findByAppointmentId(UUID appointmentId);
    Optional<QueueEntry> findByAppointmentIdAndUserId(UUID appointmentId, UUID userId);
    List<QueueEntry> findByUserIdAndQueueDateOrderByCreatedAtDesc(UUID userId, LocalDate date);
    boolean existsByQueueConfigIdAndQueueDateAndStatusIn(
            UUID configId, LocalDate date, Collection<QueueStatus> statuses);
    boolean existsByPatientIdAndDepartmentIdAndQueueDateAndStatusIn(
            UUID patientId, UUID departmentId, LocalDate date, Collection<QueueStatus> statuses);
    List<QueueEntry> findByQueueConfigIdAndQueueDateAndStatusInOrderByEligibleSinceAtAscSequenceNumberAsc(
            UUID configId, LocalDate date, Collection<QueueStatus> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<QueueEntry> findFirstById(UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<QueueEntry> findFirstByAppointmentId(UUID appointmentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<QueueEntry> findFirstByQueueConfigIdAndQueueDateAndStatusInOrderByCalledAtAsc(
            UUID configId, LocalDate date, Collection<QueueStatus> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<QueueEntry> findByQueueConfigIdAndQueueDateAndStatusAndPriorityLevelOrderByEligibleSinceAtAscSequenceNumberAsc(
            UUID configId, LocalDate date, QueueStatus status, PriorityLevel priority, Pageable pageable);

    List<QueueEntry> findByQueueConfigIdAndQueueDateAndStatusAndStartedAtIsNotNullAndCompletedAtIsNotNullOrderByCompletedAtDesc(
            UUID configId, LocalDate date, QueueStatus status, Pageable pageable);
}
