package com.careflow.lab.client;

import com.careflow.common.dto.ApiResponse;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

@FeignClient(name = "consultation-service")
public interface LabConsultationClient {
    @GetMapping("/api/consultations/{consultationId}")
    ApiResponse<Context> getById(@PathVariable("consultationId") UUID consultationId);

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Context(UUID id, UUID patientId, UUID doctorId, String status,UUID queueEntryId,UUID appointmentId) {
        public Context(UUID id,UUID patientId,UUID doctorId,String status){this(id,patientId,doctorId,status,null,null);}
    }
}
