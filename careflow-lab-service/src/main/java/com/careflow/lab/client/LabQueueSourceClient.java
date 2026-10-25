package com.careflow.lab.client;
import com.careflow.common.dto.ApiResponse;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;
@FeignClient(name="queue-service",contextId="labVisitSource")
public interface LabQueueSourceClient {
    @GetMapping("/api/queues/tickets/appointment/{id}") ApiResponse<Ticket> ticket(@PathVariable UUID id);
    @JsonIgnoreProperties(ignoreUnknown=true) record Ticket(UUID ticketId,UUID appointmentId,UUID patientId){}
}
