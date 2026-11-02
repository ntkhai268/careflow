package com.careflow.queue.controller;

import com.careflow.common.dto.ApiResponse;
import com.careflow.queue.dto.QueueEntryResponse;
import com.careflow.queue.service.QueueManagementService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
public class PatientQueueStatusController {
    private final QueueManagementService queues;

    public PatientQueueStatusController(QueueManagementService queues) {
        this.queues = queues;
    }

    @GetMapping("/api/queues/tickets/appointment/{id}/status")
    public ApiResponse<QueueEntryResponse> status(@PathVariable UUID id,
                                                  @RequestHeader("X-User-Id") UUID actor,
                                                  @RequestHeader("X-User-Role") String role) {
        return ApiResponse.success(queues.appointmentStatus(id, actor, role));
    }
}
