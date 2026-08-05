package com.careflow.consultation.model;

import com.careflow.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "consultations", indexes = {
        @Index(name = "idx_consultation_patient", columnList = "patientId"),
        @Index(name = "idx_consultation_doctor", columnList = "doctorId"),
        @Index(name = "idx_consultation_appointment", columnList = "appointmentId"),
        @Index(name = "idx_consultation_status", columnList = "status"),
        @Index(name = "idx_consultation_doctor_status", columnList = "doctorId, status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Consultation extends BaseEntity {

    private UUID appointmentId;

    @Column(nullable = false)
    private UUID patientId;

    @Column(nullable = false)
    private UUID doctorId;

    // --- Doctor Profile Snapshot (Pháp lý Y tế) ---
    @Column(length = 150)
    private String doctorName;

    @Column(length = 50)
    private String departmentCode;

    @Column(length = 100)
    private String departmentName;

    // --- Sinh hiệu (Vital Signs) ---

    @Column(precision = 4, scale = 1)
    private BigDecimal temperature;

    @Column(length = 20)
    private String bloodPressure;

    private Integer heartRate;

    private Integer spo2;

    @Column(precision = 5, scale = 1)
    private BigDecimal height;

    @Column(precision = 5, scale = 1)
    private BigDecimal weight;

    // --- Lâm sàng (Clinical) ---

    @Column(columnDefinition = "TEXT")
    private String symptoms;

    @Column(columnDefinition = "TEXT")
    private String clinicalNotes;

    // --- Chẩn đoán (Diagnosis) ---

    @Column(length = 10)
    private String icd10Code;

    @Column(length = 500)
    private String icd10Name;

    @Column(columnDefinition = "TEXT")
    private String diagnosis;

    // --- Trạng thái ---

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private ConsultationStatus status = ConsultationStatus.IN_PROGRESS;

    private LocalDateTime startedAt;

    private LocalDateTime completedAt;
}
