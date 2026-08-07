package com.careflow.consultation.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateConsultationRequest {

    // Sinh hiệu (Vital Signs)
    private BigDecimal temperature;
    private String bloodPressure;
    private Integer heartRate;
    private Integer spo2;
    private BigDecimal height;
    private BigDecimal weight;

    // Lâm sàng (Clinical)
    private String symptoms;
    private String clinicalNotes;

    // Chẩn đoán (Diagnosis)
    private String icd10Code;
    private String icd10Name;
    private String diagnosis;
}
