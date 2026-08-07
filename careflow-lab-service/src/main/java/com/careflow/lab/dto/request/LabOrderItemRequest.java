package com.careflow.lab.dto.request;

import jakarta.validation.constraints.NotBlank;

public record LabOrderItemRequest(
        @NotBlank String serviceCode,
        @NotBlank String serviceName,
        @NotBlank String servicePointId,
        Boolean required,
        String preparationInstruction
) {
}
