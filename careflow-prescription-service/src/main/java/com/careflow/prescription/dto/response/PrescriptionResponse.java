package com.careflow.prescription.dto.response;

import com.careflow.prescription.model.PrescriptionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PrescriptionResponse {

    private UUID id;
    private UUID consultationId;
    private UUID patientId;
    private UUID doctorId;
    private String diagnosis;
    private String notes;
    private LocalDate followUpDate;
    private String dispensingServicePointId;
    private LocalDateTime confirmedAt;
    private LocalDateTime dispensedAt;
    private UUID dispensedByUserId;
    private UUID replacesPrescriptionId;
    private String cancellationReason;
    private LocalDateTime cancelledAt;
    private UUID cancelledByUserId;
    private PrescriptionStatus status;
    private List<PrescriptionItemResponse> items;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
