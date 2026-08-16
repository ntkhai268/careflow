package com.careflow.emr.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MedicalRecordResponse {
    private UUID id;
    private UUID patientId;
    private String recordNumber;
    private String bloodType;
    private String medicalHistory;
    private Instant createdAt;
    private Instant updatedAt;
}
