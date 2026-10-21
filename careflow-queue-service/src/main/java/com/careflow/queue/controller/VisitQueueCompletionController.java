package com.careflow.queue.controller;

import com.careflow.common.dto.ApiResponse;
import com.careflow.common.exception.*;
import com.careflow.queue.domain.*;
import com.careflow.queue.repository.*;
import com.careflow.queue.service.QueueManagementService;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/queues/consultations")
public class VisitQueueCompletionController {
    private final QueueEntryRepository entries;private final QueueConfigRepository configs;private final QueueManagementService queues;
    public VisitQueueCompletionController(QueueEntryRepository entries,QueueConfigRepository configs,QueueManagementService queues){this.entries=entries;this.configs=configs;this.queues=queues;}
    public record Entry(UUID entryId,UUID patientId,String phase,String status){}
    @GetMapping("/{consultationId}/completion-entries")
    public ApiResponse<List<Entry>> get(@PathVariable UUID consultationId,@RequestParam UUID patientId,@RequestParam UUID initialEntryId,@RequestHeader("X-User-Id") UUID actor,@RequestHeader("X-User-Role") String role){
        if(!"DOCTOR".equals(role))throw new BusinessException(403,"Chỉ bác sĩ phụ trách được đối soát lượt khám.");
        var initial=entries.findById(initialEntryId).orElseThrow(()->new ResourceNotFoundException("QueueEntry","id",initialEntryId));
        if(initial.getQueueType()!=QueueType.CONSULTATION || initial.getConsultationPhase()!=ConsultationPhase.INITIAL || !patientId.equals(initial.getPatientId()) || (initial.getConsultationId()!=null && !consultationId.equals(initial.getConsultationId())))throw new BusinessException(409,"Phiếu ban đầu không khớp ca khám.");
        var selected=new ArrayList<QueueEntry>();selected.add(initial);
        entries.findByConsultationIdAndQueueTypeAndConsultationPhase(consultationId,QueueType.CONSULTATION,ConsultationPhase.RESULT_REVIEW).ifPresent(selected::add);
        for(var entry:selected){
            if(!patientId.equals(entry.getPatientId()))throw new BusinessException(409,"Phiếu đọc kết quả không khớp bệnh nhân.");
            var config=configs.findById(entry.getQueueConfigId()).orElseThrow(()->new BusinessException(503,"Không xác định được phòng của phiếu."));
            queues.roomDashboard(config.getRoomCode(),actor,role);
        }
        return ApiResponse.success(selected.stream().map(e->new Entry(e.getId(),e.getPatientId(),e.getConsultationPhase().name(),e.getStatus().name())).toList());
    }
}
