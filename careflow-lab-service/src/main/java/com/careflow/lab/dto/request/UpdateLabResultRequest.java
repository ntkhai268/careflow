package com.careflow.lab.dto.request;

import jakarta.validation.constraints.NotBlank;

public record UpdateLabResultRequest(
        @NotBlank String resultValue,
        String referenceRange,
        String unit,
        String resultFlag,
        String comment
) {
}
