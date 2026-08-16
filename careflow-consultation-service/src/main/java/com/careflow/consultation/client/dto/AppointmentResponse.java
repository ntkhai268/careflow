package com.careflow.consultation.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppointmentResponse {
    private UUID id;
    private UUID patientId;
    private String patientName;
    private String department;
    private String departmentDisplayName;
    private UUID doctorId;
    private String doctorName;
    private LocalDate appointmentDate;
    private String timeSlot;
    private String status;
    private String statusDisplayName;
    private String reason;
    private String notes;
    private String queueNumber;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
