package com.careflow.directory.dto;

import com.careflow.directory.model.StaffAssignment;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class StaffAssignmentResponse {
    private UUID id;
    private UUID userId;
    private String roomId;
    private String departmentCode;
    private Boolean isActive;
    private Instant createdAt;
    private Instant updatedAt;

    public static StaffAssignmentResponse from(StaffAssignment assignment) {
        return StaffAssignmentResponse.builder()
                .id(assignment.getId())
                .userId(assignment.getUserId())
                .roomId(assignment.getRoomId())
                .departmentCode(assignment.getDepartmentCode())
                .isActive(assignment.getIsActive())
                .createdAt(assignment.getCreatedAt())
                .updatedAt(assignment.getUpdatedAt())
                .build();
    }
}
