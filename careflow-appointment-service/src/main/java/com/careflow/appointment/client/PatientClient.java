package com.careflow.appointment.client;

import com.careflow.common.dto.ApiResponse;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

@FeignClient(name = "patient-service", path = "/api/patients")
public interface PatientClient {
    @GetMapping("/{patientId}/booking-context")
    ApiResponse<BookingContext> getBookingContext(@PathVariable UUID patientId);

    record BookingContext(UUID id, UUID userId, String fullName) {}

    @GetMapping("/{patientId}")
    ApiResponse<ClinicalProfile> getClinicalProfile(@PathVariable("patientId") UUID patientId);

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ClinicalProfile(UUID id, UUID userId, String fullName) {}
}
