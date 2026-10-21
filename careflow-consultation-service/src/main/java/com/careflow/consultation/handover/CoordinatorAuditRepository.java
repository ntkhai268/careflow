package com.careflow.consultation.handover;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface CoordinatorAuditRepository extends JpaRepository<CoordinatorAudit, UUID> {
    List<CoordinatorAudit> findAllByOrderByCreatedAtDesc();
}
