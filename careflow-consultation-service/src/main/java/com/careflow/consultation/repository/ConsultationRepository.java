package com.careflow.consultation.repository;

import com.careflow.consultation.model.Consultation;
import com.careflow.consultation.model.ConsultationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface ConsultationRepository extends JpaRepository<Consultation, UUID> {

    List<Consultation> findByPatientIdOrderByCreatedAtDesc(UUID patientId);

    List<Consultation> findByDoctorIdOrderByCreatedAtDesc(UUID doctorId);

    List<Consultation> findByDoctorIdAndStatus(UUID doctorId, ConsultationStatus status);

    List<Consultation> findByDoctorIdAndCreatedAtBetweenOrderByCreatedAtDesc(
            UUID doctorId, LocalDateTime startOfDay, LocalDateTime endOfDay);

    List<Consultation> findByAppointmentId(UUID appointmentId);
}
