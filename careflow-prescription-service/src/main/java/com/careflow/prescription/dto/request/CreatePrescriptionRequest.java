package com.careflow.prescription.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreatePrescriptionRequest {

    @NotNull(message = "Consultation ID is required")
    private UUID consultationId;

    @NotNull(message = "Patient ID is required")
    private UUID patientId;

    @NotNull(message = "Doctor ID is required")
    private UUID doctorId;

    private String diagnosis;
    private String notes;
    private LocalDate followUpDate;

    @NotEmpty(message = "Prescription must have at least one medicine item")
    @Valid
    private List<PrescriptionItemRequest> items;
}
