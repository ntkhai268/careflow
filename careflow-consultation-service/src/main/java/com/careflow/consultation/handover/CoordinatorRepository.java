package com.careflow.consultation.handover;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
public interface CoordinatorRepository extends JpaRepository<HandoverCoordinator, UUID> {
    boolean existsByDoctorIdAndActiveTrue(UUID doctorId);
}
