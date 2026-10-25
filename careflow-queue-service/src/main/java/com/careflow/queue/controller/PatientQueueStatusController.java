package com.careflow.queue.controller;
import com.careflow.common.dto.ApiResponse;
import com.careflow.queue.service.QueueManagementService;
import com.careflow.queue.dto.QueueEntryResponse;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;
@RestController
public class PatientQueueStatusController {
    private final QueueManagementService queues;
    public PatientQueueStatusController(QueueManagementService queues){this.queues=queues;}
    @GetMapping("/api/queues/tickets/appointment/{id}/status")
    public ApiResponse<QueueEntryResponse> status(@PathVariable UUID id,@RequestHeader("X-User-Id") UUID actor,@RequestHeader("X-User-Role") String role){return ApiResponse.success(queues.appointmentStatus(id,actor,role));}
}
