package com.careflow.queue.controller;
import com.careflow.common.dto.ApiResponse;
import com.careflow.common.exception.*;
import com.careflow.common.security.PatientOwnershipClient;
import com.careflow.queue.repository.*;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController
public class PatientQueueEntryController {
    private final QueueEntryRepository entries;private final QueueConfigRepository configs;private final PatientOwnershipClient owners;
    public PatientQueueEntryController(QueueEntryRepository entries,QueueConfigRepository configs,PatientOwnershipClient owners){this.entries=entries;this.configs=configs;this.owners=owners;}
    public record Context(UUID entryId,UUID patientId,UUID appointmentId,UUID consultationId,String queueNumber,String status,String phase,String roomId){}
    @GetMapping("/api/queues/entries/{id}/patient-context")
    public ApiResponse<Context> get(@PathVariable UUID id,@RequestHeader("X-User-Id") UUID actor,@RequestHeader("X-User-Role") String role){
        if(!"PATIENT".equals(role))throw new BusinessException(403,"Chỉ bệnh nhân được xem phiếu cá nhân.");
        var e=entries.findById(id).orElseThrow(()->new ResourceNotFoundException("QueueEntry","id",id));owners.require(e.getPatientId(),actor);
        var config=e.getQueueConfigId()==null?null:configs.findById(e.getQueueConfigId()).orElse(null);
        return ApiResponse.success(new Context(e.getId(),e.getPatientId(),e.getAppointmentId(),e.getConsultationId(),e.getQueueNumber(),e.getStatus().name(),e.getConsultationPhase()==null?null:e.getConsultationPhase().name(),config==null?e.getServicePointId():config.getRoomCode()));
    }
}
