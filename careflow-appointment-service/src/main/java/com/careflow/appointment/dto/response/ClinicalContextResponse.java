package com.careflow.appointment.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClinicalContextResponse {
    private UUID userId;
    private UUID doctorId;
    private String doctorName;
    private String department;
    private String departmentDisplayName;
    private List<RoomAssignmentResponse> rooms;
}
