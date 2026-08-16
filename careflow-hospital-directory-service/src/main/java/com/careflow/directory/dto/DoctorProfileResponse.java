package com.careflow.directory.dto;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class DoctorProfileResponse {
    private UUID id;
    private UUID userId;
    private String fullName;
    private String title;
    private String departmentCode;
    private String departmentName;
    private String assignedRoomId;
    private String specialization;
    private String licenseNumber;
    private Boolean isActive;
}
