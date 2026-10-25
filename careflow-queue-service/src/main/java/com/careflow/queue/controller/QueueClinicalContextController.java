package com.careflow.queue.controller;
import com.careflow.common.dto.ApiResponse;
import com.careflow.queue.service.QueueManagementService;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;
@RestController
@RequestMapping("/api/queues/entries")
public class QueueClinicalContextController {
    private final QueueManagementService queues;
    public QueueClinicalContextController(QueueManagementService queues){this.queues=queues;}
    @GetMapping("/{entryId}/clinical-context")
    public ApiResponse<QueueManagementService.ClinicalContext> get(@PathVariable UUID entryId,@RequestHeader("X-User-Id") UUID actor,@RequestHeader("X-User-Role") String role){return ApiResponse.success(queues.clinicalContext(entryId,actor,role));}
}
