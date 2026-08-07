package com.careflow.consultation.client;

import com.careflow.common.dto.ApiResponse;
import com.careflow.consultation.client.dto.DoctorProfileResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

@FeignClient(name = "hospital-directory-service", path = "/api/directory")
public interface DirectoryClient {

    @GetMapping("/doctors/{userId}")
    ApiResponse<DoctorProfileResponse> getDoctorByUserId(@PathVariable("userId") UUID userId);
}
