package com.careflow.queue.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record HospitalCheckInConfigRequest(
        @NotBlank @Size(max = 50) String siteId,
        @NotBlank @Size(max = 160) String facilityName,
        @DecimalMin("-90.0") @DecimalMax("90.0") double latitude,
        @DecimalMin("-180.0") @DecimalMax("180.0") double longitude,
        @DecimalMin("1.0") @DecimalMax("10000.0") double radiusMeters,
        @DecimalMin("1.0") @DecimalMax("1000.0") double maxAccuracyMeters) {
}
