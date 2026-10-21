package com.careflow.consultation.client;

import com.careflow.common.dto.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import java.util.UUID;

@FeignClient(name = "queue-service", contextId = "consultationQueue")
public interface ConsultationQueueClient {
    @GetMapping("/api/queues/tickets/appointment/{appointmentId}")
    ApiResponse<TicketContext> ticket(@PathVariable UUID appointmentId);

    @GetMapping("/api/queues/entries/{entryId}/clinical-context")
    ApiResponse<ClinicalContext> clinicalContext(@PathVariable UUID entryId);
    @GetMapping("/api/queues/entries/{entryId}/assignment-context")
    ApiResponse<AssignmentContext> assignmentContext(@PathVariable UUID entryId);
    record AssignmentContext(UUID entryId,UUID patientId,UUID appointmentId,UUID consultationId,String roomId,String departmentCode){}
    @GetMapping("/api/queues/consultations/{consultationId}/completion-entries")
    ApiResponse<java.util.List<CompletionEntry>> completionEntries(@PathVariable UUID consultationId,@org.springframework.web.bind.annotation.RequestParam UUID patientId,@org.springframework.web.bind.annotation.RequestParam UUID initialEntryId);
    @org.springframework.web.bind.annotation.PostMapping("/api/queues/entries/{entryId}/complete")
    ApiResponse<CompletionResult> complete(@PathVariable UUID entryId);
    record CompletionEntry(UUID entryId,UUID patientId,String phase,String status){}
    record CompletionResult(UUID entryId,UUID patientId,String queueStatus){}
    record ClinicalContext(UUID entryId,UUID patientId,UUID appointmentId,String patientName,String roomId,
                           String phase,String status,UUID calledByUserId){}
    record TicketContext(UUID ticketId, UUID appointmentId, UUID patientId, String status, String roomId) {}
}
