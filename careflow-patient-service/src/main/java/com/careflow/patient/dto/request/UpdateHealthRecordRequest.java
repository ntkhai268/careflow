package com.careflow.patient.dto.request;

import com.careflow.patient.model.AllergyStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class UpdateHealthRecordRequest {
    private String title;
    private LocalDate recordDate;
    private String facilityName;
    private String notes;
    private BigDecimal bloodSugar;
    private String bloodPressure;
    private BigDecimal heightCm;
    private BigDecimal weightKg;
    private BigDecimal waistCm;
    private String bloodType;
    private Integer pulse;
    private BigDecimal temperature;
    private Integer respiratoryRate;
    
    private AllergyStatus drugAllergy;
    private AllergyStatus chemicalAllergy;
    private AllergyStatus foodAllergy;
    private AllergyStatus heartDisease;
    private AllergyStatus hypertension;
    private AllergyStatus mentalIllness;
    private AllergyStatus cancer;
    private AllergyStatus asthma;
    private AllergyStatus epilepsy;
    private AllergyStatus tuberculosis;
}
