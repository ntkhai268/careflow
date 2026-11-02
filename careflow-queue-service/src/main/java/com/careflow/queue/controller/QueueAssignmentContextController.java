package com.careflow.queue.controller;

import com.careflow.common.dto.ApiResponse;
import com.careflow.common.exception.BusinessException;
import com.careflow.common.exception.ResourceNotFoundException;
import com.careflow.queue.domain.ConsultationPhase;
import com.careflow.queue.domain.QueueConfig;
import com.careflow.queue.domain.QueueEntry;
import com.careflow.queue.domain.QueueType;
import com.careflow.queue.repository.QueueConfigRepository;
import com.careflow.queue.repository.QueueEntryRepository;
import com.careflow.queue.service.QueueManagementService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Assignment metadata for an existing visit, including while its initial ticket is closed for CLS. */
@RestController
@RequestMapping("/api/queues/entries")
public class QueueAssignmentContextController {
    private final QueueEntryRepository entries;
    private final QueueConfigRepository configs;
    private final QueueManagementService queues;

    public QueueAssignmentContextController(QueueEntryRepository entries,
                                            QueueConfigRepository configs,
                                            QueueManagementService queues) {
        this.entries = entries;
        this.configs = configs;
        this.queues = queues;
    }

    public record Context(
            UUID entryId,
            UUID patientId,
            UUID appointmentId,
            UUID consultationId,
            String roomId,
            String departmentCode
    ) {}

    @GetMapping("/{entryId}/assignment-context")
    public ApiResponse<Context> get(@PathVariable UUID entryId,
                                    @RequestHeader("X-User-Id") UUID actor,
                                    @RequestHeader("X-User-Role") String role) {
        if (actor == null || !"DOCTOR".equals(role)) {
            throw new BusinessException(403, "Chỉ bác sĩ được xác minh phân công lượt khám.");
        }
        QueueEntry entry = entries.findById(entryId)
                .orElseThrow(() -> new ResourceNotFoundException("QueueEntry", "id", entryId));
        if (entry.getQueueType() != QueueType.CONSULTATION
                || entry.getConsultationPhase() != ConsultationPhase.INITIAL) {
            throw new BusinessException(409, "Phiếu không phải lượt khám ban đầu.");
        }
        QueueConfig config = configs.findById(entry.getQueueConfigId())
                .orElseThrow(() -> new BusinessException(503, "Không xác định được phòng của phiếu."));
        queues.roomDashboard(config.getRoomCode(), actor, role);
        return ApiResponse.success(new Context(
                entry.getId(),
                entry.getPatientId(),
                entry.getAppointmentId(),
                entry.getConsultationId(),
                config.getRoomCode(),
                entry.getDepartmentCode()));
    }
}
