package com.careflow.patient.dto.response;

import com.careflow.patient.model.AllergySeverity;
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
public class PatientAllergyResponse {
    private UUID id;
    private UUID patientId;
    private String allergyName;
    private String allergyGroup;
    private AllergySeverity severity;
    private String reaction;
    private String confirmedBy;
    private Instant createdAt;
}
