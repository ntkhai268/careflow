package com.careflow.notification.repository;

import com.careflow.notification.domain.PatientRecipientProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;

import java.util.UUID;

public interface PatientRecipientRepository extends JpaRepository<PatientRecipientProjection, UUID> {
    @Modifying
    @Query(value = """
            INSERT INTO notification.patient_recipients(patient_id, user_id, updated_at)
            VALUES (:patientId, :userId, :occurredAt)
            ON CONFLICT (patient_id) DO UPDATE
            SET user_id = EXCLUDED.user_id, updated_at = EXCLUDED.updated_at
            WHERE patient_recipients.updated_at <= EXCLUDED.updated_at
            """, nativeQuery = true)
    void upsert(@Param("patientId") UUID patientId, @Param("userId") UUID userId,
                @Param("occurredAt") Instant occurredAt);
}
