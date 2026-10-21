package com.careflow.patient.controller;

import com.careflow.common.dto.ApiResponse;
import com.careflow.common.exception.BusinessException;
import com.careflow.patient.service.PatientService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class PatientBookingController {
    private final PatientService patients;

    @GetMapping("/api/patients/{patientId}/booking-context")
    public ApiResponse<BookingContext> bookingContext(@PathVariable UUID patientId,
            @RequestHeader("X-User-Id") UUID actor, @RequestHeader("X-User-Role") String role) {
        if (!Set.of("PATIENT", "ADMIN").contains(role)) {
            throw new BusinessException(403, "Only the owner or ADMIN can request booking demographics");
        }
        var patient = patients.getPatientById(patientId, actor, role);
        return ApiResponse.success(new BookingContext(patient.getId(), patient.getUserId(), patient.getFullName()));
    }

    public record BookingContext(UUID id, UUID userId, String fullName) {}
}
