package com.careflow.directory.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StaffAssignmentRequest {
    @NotNull
    private UUID userId;

    @NotBlank
    private String roomId;

    @NotBlank
    private String departmentCode;

    @Builder.Default
    private Boolean isActive = true;
}
