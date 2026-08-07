package com.careflow.queue.client.dto;

import lombok.Data;

import java.util.UUID;

@Data
public class DoctorAssignmentResponse {
    private UUID userId;
    private String assignedRoomId;
    private Boolean isActive;
}
