package com.careflow.emr.client;

import com.careflow.common.dto.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Map;
import java.util.UUID;

@FeignClient(name = "patient-service")
public interface PatientClient {

    @GetMapping("/api/patients/{id}")
    ApiResponse<Map<String, Object>> getPatientById(@PathVariable("id") UUID id);
}
