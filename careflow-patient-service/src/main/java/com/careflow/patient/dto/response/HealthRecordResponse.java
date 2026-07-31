package com.careflow.patient.dto.response;

import com.careflow.patient.model.AllergyStatus;
import com.careflow.patient.model.HealthRecord;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Builder
public record HealthRecordResponse(
        UUID id,
        UUID patientId,
        String title,
        LocalDate recordDate,
        String facilityName,
        String notes,
        BigDecimal bloodSugar,
        String bloodPressure,
        BigDecimal heightCm,
        BigDecimal weightKg,
        BigDecimal bmi,
        BigDecimal waistCm,
        String bloodType,
        Integer pulse,
        BigDecimal temperature,
        Integer respiratoryRate,
        AllergyStatus drugAllergy,
        AllergyStatus chemicalAllergy,
        AllergyStatus foodAllergy,
        AllergyStatus heartDisease,
        AllergyStatus hypertension,
        AllergyStatus mentalIllness,
        AllergyStatus cancer,
        AllergyStatus asthma,
        AllergyStatus epilepsy,
        AllergyStatus tuberculosis,
        Instant createdAt,
        Instant updatedAt,
        List<HealthRecordFileResponse> files
) {
    public static HealthRecordResponse from(HealthRecord record, String baseUrl) {
        return HealthRecordResponse.builder()
                .id(record.getId())
                .patientId(record.getPatientId())
                .title(record.getTitle())
                .recordDate(record.getRecordDate())
                .facilityName(record.getFacilityName())
                .notes(record.getNotes())
                .bloodSugar(record.getBloodSugar())
                .bloodPressure(record.getBloodPressure())
                .heightCm(record.getHeightCm())
                .weightKg(record.getWeightKg())
                .bmi(record.getBmi())
                .waistCm(record.getWaistCm())
                .bloodType(record.getBloodType())
                .pulse(record.getPulse())
                .temperature(record.getTemperature())
                .respiratoryRate(record.getRespiratoryRate())
                .drugAllergy(record.getDrugAllergy())
                .chemicalAllergy(record.getChemicalAllergy())
                .foodAllergy(record.getFoodAllergy())
                .heartDisease(record.getHeartDisease())
                .hypertension(record.getHypertension())
                .mentalIllness(record.getMentalIllness())
                .cancer(record.getCancer())
                .asthma(record.getAsthma())
                .epilepsy(record.getEpilepsy())
                .tuberculosis(record.getTuberculosis())
                .createdAt(record.getCreatedAt())
                .updatedAt(record.getUpdatedAt())
                .files(record.getFiles() != null ?
                       record.getFiles().stream()
                             .map(f -> HealthRecordFileResponse.from(f, baseUrl))
                             .collect(Collectors.toList()) :
                       List.of())
                .build();
    }
}
