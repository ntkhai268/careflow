package com.careflow.ai.repository;

import com.careflow.ai.entity.AiClinicalInteraction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AiClinicalInteractionRepository extends JpaRepository<AiClinicalInteraction, UUID> {

    List<AiClinicalInteraction> findByConsultationIdOrderByCreatedAtDesc(UUID consultationId);

    List<AiClinicalInteraction> findByDoctorIdOrderByCreatedAtDesc(UUID doctorId);
}
