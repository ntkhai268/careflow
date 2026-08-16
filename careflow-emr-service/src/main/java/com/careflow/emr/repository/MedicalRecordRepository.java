package com.careflow.emr.repository;

import com.careflow.emr.model.MedicalRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface MedicalRecordRepository extends JpaRepository<MedicalRecord, UUID> {
    Optional<MedicalRecord> findByPatientId(UUID patientId);
    boolean existsByPatientId(UUID patientId);
    Optional<MedicalRecord> findByRecordNumber(String recordNumber);
}
