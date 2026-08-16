package com.careflow.appointment.dto.request;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateFollowUpRequest {
    @NotNull private UUID consultationId;
    @NotNull private UUID patientId;
    private String department;
    @NotNull @FutureOrPresent private LocalDate recommendedDate;
    private String note;
    private String timeSlot;
}
