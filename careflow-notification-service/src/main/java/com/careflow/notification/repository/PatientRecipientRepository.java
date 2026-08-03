package com.careflow.notification.repository;

import com.careflow.notification.domain.PatientRecipientProjection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PatientRecipientRepository extends JpaRepository<PatientRecipientProjection, UUID> {
}
