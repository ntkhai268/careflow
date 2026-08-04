package com.careflow.notification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "patient_recipients", schema = "notification")
@Getter @Setter @NoArgsConstructor
public class PatientRecipientProjection {
    @Id
    @Column(name = "patient_id")
    private UUID patientId;
    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
