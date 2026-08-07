package com.careflow.emr.model;

import com.careflow.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "medical_records", indexes = {
        @Index(name = "idx_emr_patient", columnList = "patientId", unique = true),
        @Index(name = "uk_emr_record_number", columnList = "recordNumber", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MedicalRecord extends BaseEntity {

    @Column(nullable = false, unique = true)
    private UUID patientId;

    @Column(nullable = false, unique = true, length = 50)
    private String recordNumber;

    @Column(length = 10)
    private String bloodType;

    @Column(columnDefinition = "TEXT")
    private String medicalHistory;
}
