package com.careflow.prescription.repository;

import com.careflow.prescription.model.Prescription;
import com.careflow.prescription.model.PrescriptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PrescriptionRepository extends JpaRepository<Prescription, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select prescription from Prescription prescription where prescription.id = :id")
    Optional<Prescription> findByIdForUpdate(@Param("id") UUID id);

    List<Prescription> findByConsultationId(UUID consultationId);

    List<Prescription> findByPatientIdOrderByCreatedAtDesc(UUID patientId);

    List<Prescription> findByDoctorIdOrderByCreatedAtDesc(UUID doctorId);

    List<Prescription> findByStatus(PrescriptionStatus status);
}
