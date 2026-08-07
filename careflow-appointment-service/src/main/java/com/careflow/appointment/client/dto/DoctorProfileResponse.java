package com.careflow.appointment.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
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
