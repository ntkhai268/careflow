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
    Optional<QueueEntry> findByPrescriptionId(UUID prescriptionId);
    Optional<QueueEntry> findByConsultationIdAndQueueTypeAndConsultationPhase(
            UUID consultationId, QueueType queueType, ConsultationPhase consultationPhase);
    List<QueueEntry> findByPatientIdAndQueueTypeAndConsultationPhaseAndStatusInOrderByCreatedAtDesc(
            UUID patientId, QueueType queueType, ConsultationPhase consultationPhase,
            Collection<QueueStatus> statuses);
    Optional<QueueEntry> findByLabOrderIdAndServicePointId(UUID labOrderId, String servicePointId);
    Optional<QueueEntry> findFirstByLabOrderIdOrderByCreatedAtDesc(UUID labOrderId);
    Optional<QueueEntry> findByAppointmentIdAndUserId(UUID appointmentId, UUID userId);
    List<QueueEntry> findByUserIdAndQueueDateOrderByCreatedAtDesc(UUID userId, LocalDate date);
    Optional<QueueEntry> findFirstByPatientIdAndUserIdIsNotNullOrderByCreatedAtDesc(UUID patientId);
    Optional<QueueEntry> findFirstByPatientIdAndUserIdAndQueueDateAndStatusInOrderByCreatedAtDesc(
            UUID patientId, UUID userId, LocalDate queueDate, Collection<QueueStatus> statuses);
    Optional<QueueEntry> findFirstByPatientIdAndQueueDateAndStatusInOrderByCreatedAtDesc(
            UUID patientId, LocalDate queueDate, Collection<QueueStatus> statuses);
    boolean existsByQueueConfigIdAndQueueDateAndStatusIn(
            UUID configId, LocalDate date, Collection<QueueStatus> statuses);
    boolean existsByPatientIdAndDepartmentIdAndQueueDateAndStatusIn(
            UUID patientId, UUID departmentId, LocalDate date, Collection<QueueStatus> statuses);
    List<QueueEntry> findByQueueConfigIdAndQueueDateAndStatusInOrderByEligibleSinceAtAscSequenceNumberAsc(
            UUID configId, LocalDate date, Collection<QueueStatus> statuses);
    List<QueueEntry> findByServicePointIdAndQueueDateAndStatusInOrderByEligibleSinceAtAscSequenceNumberAsc(
            String servicePointId, LocalDate date, Collection<QueueStatus> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<QueueEntry> findFirstById(UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<QueueEntry> findFirstByAppointmentId(UUID appointmentId);

    List<QueueEntry> findByQueueConfigIdAndQueueDateAndStatusAndStartedAtIsNotNullAndCompletedAtIsNotNullOrderByCompletedAtDesc(
            UUID configId, LocalDate date, QueueStatus status, Pageable pageable);
}
