package com.careflow.directory.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateDoctorProfileRequest {

    @NotBlank(message = "Họ tên không được để trống")
    private String fullName;

    private String title;
    private String departmentCode;
    private String assignedRoomId;
    private String specialization;
    private String licenseNumber;
    private Boolean isActive;
}
