package com.careflow.patient.repository;

import com.careflow.patient.model.HealthRecordFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface HealthRecordFileRepository extends JpaRepository<HealthRecordFile, UUID> {
}
