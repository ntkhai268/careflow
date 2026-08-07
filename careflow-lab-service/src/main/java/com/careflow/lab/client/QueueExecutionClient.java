package com.careflow.lab.client;

import com.careflow.common.constants.AppConstants;
import com.careflow.common.dto.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.Map;
import java.util.UUID;

@FeignClient(name = "queue-service", url = "${app.queue-service.url:http://careflow-queue-service:8084}")
public interface QueueExecutionClient {
    @GetMapping("/api/queues/lab-orders/{labOrderId}/current")
    ApiResponse<Map<String, Object>> getCurrent(
            @PathVariable UUID labOrderId,
            @RequestHeader(AppConstants.HEADER_USER_ID) UUID userId,
            @RequestHeader(AppConstants.HEADER_USER_ROLE) String role);
}
