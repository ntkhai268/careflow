package com.careflow.emr.model;

import com.careflow.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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
    public static final String UNKNOWN_BLOOD_TYPE = "Chưa rõ";

    @Column(nullable = false, unique = true)
    private UUID patientId;

    @Column(nullable = false, unique = true, length = 50)
    private String recordNumber;

    @Column(length = 10)
    private String bloodType;

    @Column(columnDefinition = "TEXT")
    private String medicalHistory;

    public static MedicalRecord create(UUID patientId,
                                       String recordNumber,
                                       String bloodType,
                                       String medicalHistory) {
        return MedicalRecord.builder()
                .patientId(patientId)
                .recordNumber(recordNumber)
                .bloodType(bloodType != null ? bloodType : UNKNOWN_BLOOD_TYPE)
                .medicalHistory(medicalHistory)
                .build();
    }

    public void applyUpdates(String updatedBloodType, String updatedMedicalHistory) {
        if (updatedBloodType != null) {
            this.bloodType = updatedBloodType;
        }
        if (updatedMedicalHistory != null) {
            this.medicalHistory = updatedMedicalHistory;
        }
    }
}
