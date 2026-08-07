package com.careflow.lab.client;

import com.careflow.common.dto.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.Map;
import java.util.UUID;

@FeignClient(name = "patient-service")
public interface PatientIdentityClient {

    @GetMapping("/api/patients/user/{userId}")
    ApiResponse<Map<String, Object>> getByUserId(
            @PathVariable("userId") UUID userId,
            @RequestHeader("X-User-Id") UUID requesterId,
            @RequestHeader("X-User-Role") String role);
}
