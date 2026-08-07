package com.careflow.lab.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record CreateLabOrderRequest(
        @NotNull UUID consultationId,
        @NotNull UUID patientId,
        @NotEmpty @Valid List<LabOrderItemRequest> items,
        String clinicalNote,
        Boolean paymentRequired,
        UUID actorId,
        UUID departmentId
) {
    public CreateLabOrderRequest(UUID consultationId,
                                 UUID patientId,
                                 List<LabOrderItemRequest> items,
                                 String clinicalNote,
                                 Boolean paymentRequired,
                                 UUID actorId) {
        this(consultationId, patientId, items, clinicalNote, paymentRequired, actorId, null);
    }
}
