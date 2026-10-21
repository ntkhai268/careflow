package com.careflow.consultation.client;

import com.careflow.common.dto.ApiResponse;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import java.util.List;
import java.util.UUID;

@FeignClient(name="lab-service", contextId="consultationLab")
public interface ConsultationLabClient {
    @GetMapping("/api/labs/orders/consultation/{id}")
    ApiResponse<List<Order>> orders(@PathVariable UUID id);
    @JsonIgnoreProperties(ignoreUnknown=true)
    record Order(UUID id, UUID consultationId, UUID patientId, String status) {}
}
