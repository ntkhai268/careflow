package com.careflow.lab.repository;

import com.careflow.lab.model.LabOrder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface LabOrderRepository extends JpaRepository<LabOrder, UUID> {
    List<LabOrder> findByConsultationIdOrderByCreatedAtDesc(UUID consultationId);
    List<LabOrder> findByPatientIdOrderByCreatedAtDesc(UUID patientId);
}
