package com.careflow.queue.repository;

import com.careflow.queue.domain.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface QueueEntryRepository extends JpaRepository<QueueEntry, UUID> {
    Optional<QueueEntry> findByAppointmentId(UUID appointmentId);
    Optional<QueueEntry> findByAppointmentIdAndUserId(UUID appointmentId, UUID userId);
    List<QueueEntry> findByUserIdAndQueueDateOrderByCreatedAtDesc(UUID userId, LocalDate date);
    boolean existsByQueueConfigIdAndQueueDateAndStatus(UUID configId, LocalDate date, QueueStatus status);
    List<QueueEntry> findByQueueConfigIdAndQueueDateAndStatusInOrderByEligibleSinceAtAscSequenceNumberAsc(
            UUID configId, LocalDate date, Collection<QueueStatus> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from QueueEntry e where e.id = :id")
    Optional<QueueEntry> findByIdForUpdate(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from QueueEntry e where e.appointmentId = :appointmentId")
    Optional<QueueEntry> findByAppointmentIdForUpdate(@Param("appointmentId") UUID appointmentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from QueueEntry e where e.queueConfigId = :configId and e.queueDate = :date " +
            "and e.status = com.careflow.queue.domain.QueueStatus.CHECKED_IN and e.priorityLevel = :priority " +
            "order by e.eligibleSinceAt asc, e.sequenceNumber asc")
    List<QueueEntry> findCandidatesForUpdate(@Param("configId") UUID configId,
                                             @Param("date") LocalDate date,
                                             @Param("priority") PriorityLevel priority,
                                             Pageable pageable);

    @Query("select e from QueueEntry e where e.queueConfigId = :configId and e.queueDate = :date " +
            "and e.status = 'COMPLETED' and e.startedAt is not null and e.completedAt is not null " +
            "order by e.completedAt desc")
    List<QueueEntry> findRecentCompleted(@Param("configId") UUID configId,
                                         @Param("date") LocalDate date,
                                         Pageable pageable);
}
