package com.careflow.queue.dto;

import java.time.LocalDate;
import java.util.UUID;

public record VisitTicketResponse(
        UUID ticketId,
        String ticketCode,
        UUID appointmentId,
        UUID patientId,
        String queueNumber,
        String department,
        String departmentDisplayName,
        String roomId,
        String roomDisplayName,
        LocalDate appointmentDate,
        String timeSlot,
        String qrToken,
        String status) {
}
