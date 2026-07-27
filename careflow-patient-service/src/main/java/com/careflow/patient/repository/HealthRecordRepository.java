package com.careflow.patient.repository;

import com.careflow.patient.model.HealthRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface HealthRecordRepository extends JpaRepository<HealthRecord, UUID> {
    List<HealthRecord> findByPatientIdOrderByRecordDateDesc(UUID patientId);
    Optional<HealthRecord> findByIdAndPatientId(UUID id, UUID patientId);
}
