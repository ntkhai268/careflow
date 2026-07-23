package com.careflow.prescription.repository;

import com.careflow.prescription.model.Prescription;
import com.careflow.prescription.model.PrescriptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PrescriptionRepository extends JpaRepository<Prescription, UUID> {

    List<Prescription> findByConsultationId(UUID consultationId);

    List<Prescription> findByPatientIdOrderByCreatedAtDesc(UUID patientId);

    List<Prescription> findByDoctorIdOrderByCreatedAtDesc(UUID doctorId);

    List<Prescription> findByStatus(PrescriptionStatus status);
}
