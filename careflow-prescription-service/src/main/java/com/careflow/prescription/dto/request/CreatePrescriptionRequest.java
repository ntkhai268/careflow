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

    private UUID doctorId;

    private String diagnosis;
    private String notes;
    @jakarta.validation.constraints.FutureOrPresent
    private LocalDate followUpDate;
    @jakarta.validation.constraints.Pattern(regexp="[A-Z][A-Z0-9_]{2,49}")
    private String followUpDepartment;
    @jakarta.validation.constraints.Pattern(regexp="([01][0-9]|2[0-3]):[0-5][0-9]-([01][0-9]|2[0-3]):[0-5][0-9]")
    private String followUpTimeSlot;


    @NotEmpty(message = "Prescription must have at least one medicine item")
    @Valid
    private List<PrescriptionItemRequest> items;
}
