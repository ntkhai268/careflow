package com.careflow.queue.dto;

import com.careflow.queue.domain.PriorityLevel;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ManualIntakeRequest(@NotNull UUID patientId, UUID userId,
                                  @NotNull UUID departmentId, @NotNull PriorityLevel priorityLevel,
                                  @jakarta.validation.constraints.Size(max=50) String roomId,
                                  @jakarta.validation.constraints.Pattern(regexp="[A-Z][A-Z0-9_]{2,49}") String serviceCode) {
    public ManualIntakeRequest(UUID patientId,UUID userId,UUID departmentId,PriorityLevel priorityLevel){this(patientId,userId,departmentId,priorityLevel,null,null);}
}
