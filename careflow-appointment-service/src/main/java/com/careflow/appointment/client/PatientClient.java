package com.careflow.appointment.client;

import com.careflow.common.dto.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.UUID;

@FeignClient(name = "patient-service", path = "/api/patients")
public interface PatientClient {
    @GetMapping("/{patientId}/owner-user-id")
    ApiResponse<UUID> getOwnerUserId(@PathVariable UUID patientId,
                                     @RequestHeader("X-User-Id") UUID requesterUserId,
                                     @RequestHeader("X-User-Role") String requesterRole);
}
