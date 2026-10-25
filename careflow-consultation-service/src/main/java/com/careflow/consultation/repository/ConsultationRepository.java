package com.careflow.consultation.repository;

import com.careflow.consultation.model.Consultation;
import com.careflow.consultation.model.ConsultationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface ConsultationRepository extends JpaRepository<Consultation, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Consultation c where c.id = :id")
    java.util.Optional<Consultation> findByIdForUpdate(UUID id);

    @Query("select c from Consultation c where coalesce(c.responsibleDoctorId, c.doctorId) = :doctorId and c.status = :status")
    List<Consultation> findCurrentByDoctorAndStatus(UUID doctorId, ConsultationStatus status);

    @Query("select c from Consultation c where coalesce(c.responsibleDoctorId, c.doctorId) = :doctorId order by c.createdAt desc")
    List<Consultation> findCurrentByDoctor(UUID doctorId);

    @Query("select c from Consultation c where coalesce(c.responsibleDoctorId, c.doctorId) = :doctorId and c.createdAt between :start and :end order by c.createdAt desc")
    List<Consultation> findCurrentByDoctorAndDate(UUID doctorId, Instant start, Instant end);

    List<Consultation> findByStatusIn(java.util.Set<ConsultationStatus> statuses);

    List<Consultation> findByPatientIdOrderByCreatedAtDesc(UUID patientId);

    List<Consultation> findByDoctorIdOrderByCreatedAtDesc(UUID doctorId);

    List<Consultation> findByDoctorIdAndStatus(UUID doctorId, ConsultationStatus status);

    List<Consultation> findByDoctorIdAndCreatedAtBetweenOrderByCreatedAtDesc(
            UUID doctorId, Instant startOfDay, Instant endOfDay);

    java.util.Optional<Consultation> findByQueueEntryId(UUID queueEntryId);

    List<Consultation> findByAppointmentId(UUID appointmentId);
}
