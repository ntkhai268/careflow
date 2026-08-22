package com.careflow.ai.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VoiceExtractedFields {
    private String heartRate;
    private String bloodPressure;
    private String temperature;
    private String spo2;
    private String height;
    private String weight;
    private String symptoms;
    private String clinicalNotes;
    private String icd10Code;
    private String icd10Name;
    private String diagnosis;
}
