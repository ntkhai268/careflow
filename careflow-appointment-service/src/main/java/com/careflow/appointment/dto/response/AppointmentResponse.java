package com.careflow.appointment.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppointmentResponse {
    private UUID id;
    private UUID patientId;
    private UUID ownerUserId;
    private String patientName;
    private String department;
    private String departmentDisplayName;
    private UUID departmentId;
    private String roomId;
    private String roomDisplayName;
    private UUID doctorId;
    private String doctorName;
    private LocalDate appointmentDate;
    private String timeSlot;
    private String status;
    private String statusDisplayName;
    private String reason;
    private String notes;
    private String queueNumber;
    private Instant createdAt;
    private Instant updatedAt;
}

