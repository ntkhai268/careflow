package com.careflow.patient.client;

import com.careflow.common.dto.ApiResponse;
import com.careflow.patient.client.dto.AssignmentAccessResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.UUID;

@FeignClient(name = "appointment-service", path = "/api/appointments")
public interface AssignmentClient {

    @GetMapping("/access/patient/{patientId}")
    ApiResponse<AssignmentAccessResponse> getPatientAccess(
            @PathVariable("patientId") UUID patientId,
            @RequestParam(value = "appointmentId", required = false) UUID appointmentId,
            @RequestParam(value = "roomId", required = false) String roomId,
            @RequestHeader("X-User-Id") UUID requesterUserId,
            @RequestHeader("X-User-Role") String requesterRole);
}
