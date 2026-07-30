package com.careflow.patient.model;

import com.careflow.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "health_records")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HealthRecord extends BaseEntity {

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(name = "record_date", nullable = false)
    private LocalDate recordDate;

    @Column(name = "facility_name", nullable = false, length = 255)
    private String facilityName;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "blood_sugar")
    private BigDecimal bloodSugar;

    @Column(name = "blood_pressure", length = 10)
    private String bloodPressure;

    @Column(name = "height_cm")
    private BigDecimal heightCm;

    @Column(name = "weight_kg")
    private BigDecimal weightKg;

    private BigDecimal bmi;

    @Column(name = "waist_cm")
    private BigDecimal waistCm;

    @Column(name = "blood_type", length = 5)
    private String bloodType;

    private Integer pulse;
    private BigDecimal temperature;

    @Column(name = "respiratory_rate")
    private Integer respiratoryRate;

    @Enumerated(EnumType.STRING)
    @Column(name = "drug_allergy")
    private AllergyStatus drugAllergy;

    @Enumerated(EnumType.STRING)
    @Column(name = "chemical_allergy")
    private AllergyStatus chemicalAllergy;

    @Enumerated(EnumType.STRING)
    @Column(name = "food_allergy")
    private AllergyStatus foodAllergy;

    @Enumerated(EnumType.STRING)
    @Column(name = "heart_disease")
    private AllergyStatus heartDisease;

    @Enumerated(EnumType.STRING)
    private AllergyStatus hypertension;

    @Enumerated(EnumType.STRING)
    @Column(name = "mental_illness")
    private AllergyStatus mentalIllness;

    @Enumerated(EnumType.STRING)
    private AllergyStatus cancer;

    @Enumerated(EnumType.STRING)
    private AllergyStatus asthma;

    @Enumerated(EnumType.STRING)
    private AllergyStatus epilepsy;

    @Enumerated(EnumType.STRING)
    private AllergyStatus tuberculosis;

    @OneToMany(mappedBy = "healthRecord", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<HealthRecordFile> files = new ArrayList<>();

    public void addFile(HealthRecordFile file) {
        files.add(file);
        file.setHealthRecord(this);
    }
}
