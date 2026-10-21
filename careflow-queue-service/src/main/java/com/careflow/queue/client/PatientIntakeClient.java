package com.careflow.queue.client;

import com.careflow.common.dto.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import java.util.UUID;

@FeignClient(name = "patient-service", contextId = "queuePatientIntake", path = "/api/patients")
public interface PatientIntakeClient {
    @GetMapping("/reception/{patientId}")
    ApiResponse<ReceptionPatient> minimum(@PathVariable("patientId") UUID patientId,@org.springframework.web.bind.annotation.RequestParam("roomId") String roomId);
    record ReceptionPatient(UUID id,UUID userId,String fullName){}
    @GetMapping("/{patientId}/owner-user-id")
    ApiResponse<UUID> owner(@PathVariable("patientId") UUID patientId);
}
