package com.careflow.directory.dto.request;

import jakarta.validation.constraints.*;
import java.time.Instant;

public record ServicePriceRequest(
        @NotBlank @Pattern(regexp="[A-Z][A-Z0-9_]{2,49}") String code,
        @NotBlank @Size(max=150) String name,
        @Size(max=500) String description,
        @Min(0) @Max(1000000000000L) long amount,
        @Min(1) @Max(1440) int estimatedMinutes,
        boolean active, Instant effectiveAt, @Min(0) long expectedVersion) {}
