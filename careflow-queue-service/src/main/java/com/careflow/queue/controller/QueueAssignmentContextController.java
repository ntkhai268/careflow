package com.careflow.queue.controller;

import com.careflow.common.dto.ApiResponse;
import com.careflow.common.exception.*;
import com.careflow.queue.domain.*;
import com.careflow.queue.repository.*;
import com.careflow.queue.service.QueueManagementService;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

/** Assignment metadata for an existing visit, including while its initial ticket is closed for CLS. */
@RestController
@RequestMapping("/api/queues/entries")
public class QueueAssignmentContextController {
    private final QueueEntryRepository entries;
    private final QueueConfigRepository configs;
    private final QueueManagementService queues;
    public QueueAssignmentContextController(QueueEntryRepository entries,QueueConfigRepository configs,QueueManagementService queues){this.entries=entries;this.configs=configs;this.queues=queues;}
    public record Context(UUID entryId,UUID patientId,UUID appointmentId,UUID consultationId,String roomId,String departmentCode){}
    @GetMapping("/{entryId}/assignment-context")
    public ApiResponse<Context> get(@PathVariable UUID entryId,@RequestHeader("X-User-Id") UUID actor,@RequestHeader("X-User-Role") String role){
        if(actor==null || !"DOCTOR".equals(role))throw new BusinessException(403,"Chỉ bác sĩ được xác minh phân công lượt khám.");
        var entry=entries.findById(entryId).orElseThrow(()->new ResourceNotFoundException("QueueEntry","id",entryId));
        if(entry.getQueueType()!=QueueType.CONSULTATION || entry.getConsultationPhase()!=ConsultationPhase.INITIAL)throw new BusinessException(409,"Phiếu không phải lượt khám ban đầu.");
        var config=configs.findById(entry.getQueueConfigId()).orElseThrow(()->new BusinessException(503,"Không xác định được phòng của phiếu."));
        queues.roomDashboard(config.getRoomCode(),actor,role);
        return ApiResponse.success(new Context(entry.getId(),entry.getPatientId(),entry.getAppointmentId(),entry.getConsultationId(),config.getRoomCode(),entry.getDepartmentCode()));
    }
}
