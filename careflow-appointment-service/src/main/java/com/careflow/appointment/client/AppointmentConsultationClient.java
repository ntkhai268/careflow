package com.careflow.appointment.client;

import com.careflow.common.dto.ApiResponse;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

@FeignClient(name = "consultation-service")
public interface AppointmentConsultationClient {
    @GetMapping("/api/consultations/handovers/patient-access/{patientId}")
    ApiResponse<PatientResponsibility> patientAccess(@PathVariable UUID patientId);

    record PatientResponsibility(boolean allowed, java.util.List<UUID> supersededAppointmentIds) {}
    @GetMapping("/api/consultations/appointment/{appointmentId}")
    ApiResponse<java.util.List<Context>> getByAppointment(@PathVariable("appointmentId") UUID appointmentId);
    @GetMapping("/api/consultations/{consultationId}")
    ApiResponse<Context> getById(@PathVariable("consultationId") UUID consultationId);

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Context(UUID id, UUID patientId, UUID doctorId, String status) {}
}
