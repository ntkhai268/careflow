package com.careflow.queue.dto;

import com.careflow.queue.domain.MissedPolicy;
import jakarta.validation.constraints.*;

public record QueueConfigRequest(
        @NotBlank @Size(max = 100) String departmentName,
        @NotBlank @Pattern(regexp = "^[A-Za-z0-9-]{1,10}$") String queuePrefix,
        @Size(max = 50) String roomCode,
        @Min(1) @Max(20) int priorityRatioN,
        @Min(1) @Max(20) int normalRatioM,
        @Min(1) @Max(240) int avgConsultationMinutes,
        @Min(1) @Max(20) int nearTurnThreshold,
        @NotNull MissedPolicy missedPolicy,
        boolean active) {
}
