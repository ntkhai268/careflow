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
public class CreateDoctorProfileRequest {

    @NotNull(message = "UserId không được để trống")
    private UUID userId;

    @NotBlank(message = "Họ tên không được để trống")
    private String fullName;

    private String title;
    private String departmentCode;
    private String assignedRoomId;
    private String specialization;
    private String licenseNumber;

    @Builder.Default
    private Boolean isActive = true;
}
