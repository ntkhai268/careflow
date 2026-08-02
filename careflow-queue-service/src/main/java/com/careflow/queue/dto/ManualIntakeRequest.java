package com.careflow.queue.dto;

import com.careflow.queue.domain.PriorityLevel;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ManualIntakeRequest(@NotNull UUID patientId, @NotNull UUID userId,
                                  @NotNull UUID departmentId, @NotNull PriorityLevel priorityLevel) {
}
