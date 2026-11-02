package com.careflow.patient.model;

import com.careflow.common.model.BaseEntity;
import com.careflow.patient.dto.request.CreateHealthRecordRequest;
import com.careflow.patient.dto.request.UpdateHealthRecordRequest;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
    private static final BigDecimal CENTIMETERS_PER_METER = new BigDecimal("100");

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

    public static HealthRecord fromCreateRequest(UUID patientId, CreateHealthRecordRequest request) {
        return HealthRecord.builder()
                .patientId(patientId)
                .title(request.getTitle())
                .recordDate(request.getRecordDate())
                .facilityName(request.getFacilityName())
                .notes(request.getNotes())
                .bloodSugar(request.getBloodSugar())
                .bloodPressure(request.getBloodPressure())
                .heightCm(request.getHeightCm())
                .weightKg(request.getWeightKg())
                .bmi(calculateBmi(request.getHeightCm(), request.getWeightKg()))
                .waistCm(request.getWaistCm())
                .bloodType(request.getBloodType())
                .pulse(request.getPulse())
                .temperature(request.getTemperature())
                .respiratoryRate(request.getRespiratoryRate())
                .drugAllergy(request.getDrugAllergy())
                .chemicalAllergy(request.getChemicalAllergy())
                .foodAllergy(request.getFoodAllergy())
                .heartDisease(request.getHeartDisease())
                .hypertension(request.getHypertension())
                .mentalIllness(request.getMentalIllness())
                .cancer(request.getCancer())
                .asthma(request.getAsthma())
                .epilepsy(request.getEpilepsy())
                .tuberculosis(request.getTuberculosis())
                .build();
    }

    public void applyUpdates(UpdateHealthRecordRequest request) {
        if (request.getTitle() != null) {
            this.title = request.getTitle();
        }
        if (request.getRecordDate() != null) {
            this.recordDate = request.getRecordDate();
        }
        if (request.getFacilityName() != null) {
            this.facilityName = request.getFacilityName();
        }
        this.notes = request.getNotes();
        this.bloodSugar = request.getBloodSugar();
        this.bloodPressure = request.getBloodPressure();
        this.heightCm = request.getHeightCm();
        this.weightKg = request.getWeightKg();
        this.waistCm = request.getWaistCm();
        this.bloodType = request.getBloodType();
        this.pulse = request.getPulse();
        this.temperature = request.getTemperature();
        this.respiratoryRate = request.getRespiratoryRate();
        this.drugAllergy = request.getDrugAllergy();
        this.chemicalAllergy = request.getChemicalAllergy();
        this.foodAllergy = request.getFoodAllergy();
        this.heartDisease = request.getHeartDisease();
        this.hypertension = request.getHypertension();
        this.mentalIllness = request.getMentalIllness();
        this.cancer = request.getCancer();
        this.asthma = request.getAsthma();
        this.epilepsy = request.getEpilepsy();
        this.tuberculosis = request.getTuberculosis();
        this.bmi = calculateBmi(this.heightCm, this.weightKg);
    }

    public static BigDecimal calculateBmi(BigDecimal heightCm, BigDecimal weightKg) {
        if (heightCm != null && weightKg != null && heightCm.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal heightInMeters = heightCm.divide(CENTIMETERS_PER_METER, 2, RoundingMode.HALF_UP);
            return weightKg.divide(heightInMeters.multiply(heightInMeters), 2, RoundingMode.HALF_UP);
        }
        return null;
    }

    public void addFile(HealthRecordFile file) {
        files.add(file);
        file.setHealthRecord(this);
    }
}
