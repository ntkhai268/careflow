package com.careflow.notification.controller;

import com.careflow.common.dto.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
    @GetMapping("/info")
    public ApiResponse<Map<String, String>> info() {
        return ApiResponse.success(Map.of(
                "webSocketEndpoint", "/ws",
                "patientDestination", "/user/queue/notifications",
                "doctorTopic", "/topic/queues/departments/{departmentId}"));
    }
}
