package com.careflow.emr.client;

import com.careflow.common.dto.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@FeignClient(name = "prescription-service")
public interface PrescriptionClient {

    @GetMapping("/api/prescriptions/patient/{patientId}")
    ApiResponse<List<Map<String, Object>>> getPrescriptionsByPatient(@PathVariable("patientId") UUID patientId);
}
