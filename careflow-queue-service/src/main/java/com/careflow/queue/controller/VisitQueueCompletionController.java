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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/queues/consultations")
public class VisitQueueCompletionController {
    private final QueueEntryRepository entries;
    private final QueueConfigRepository configs;
    private final QueueManagementService queues;

    public VisitQueueCompletionController(QueueEntryRepository entries,
                                          QueueConfigRepository configs,
                                          QueueManagementService queues) {
        this.entries = entries;
        this.configs = configs;
        this.queues = queues;
    }

    public record Entry(UUID entryId, UUID patientId, String phase, String status) {}

    @GetMapping("/{consultationId}/completion-entries")
    public ApiResponse<List<Entry>> get(@PathVariable UUID consultationId,
                                        @RequestParam UUID patientId,
                                        @RequestParam UUID initialEntryId,
                                        @RequestHeader("X-User-Id") UUID actor,
                                        @RequestHeader("X-User-Role") String role) {
        if (!"DOCTOR".equals(role)) {
            throw new BusinessException(403, "Chỉ bác sĩ phụ trách được đối soát lượt khám.");
        }
        QueueEntry initial = entries.findById(initialEntryId)
                .orElseThrow(() -> new ResourceNotFoundException("QueueEntry", "id", initialEntryId));
        if (initial.getQueueType() != QueueType.CONSULTATION
                || initial.getConsultationPhase() != ConsultationPhase.INITIAL
                || !patientId.equals(initial.getPatientId())
                || (initial.getConsultationId() != null && !consultationId.equals(initial.getConsultationId()))) {
            throw new BusinessException(409, "Phiếu ban đầu không khớp ca khám.");
        }
        List<QueueEntry> selected = new ArrayList<>();
        selected.add(initial);
        entries.findByConsultationIdAndQueueTypeAndConsultationPhase(
                        consultationId, QueueType.CONSULTATION, ConsultationPhase.RESULT_REVIEW)
                .ifPresent(selected::add);
        for (QueueEntry entry : selected) {
            if (!patientId.equals(entry.getPatientId())) {
                throw new BusinessException(409, "Phiếu đọc kết quả không khớp bệnh nhân.");
            }
            QueueConfig config = configs.findById(entry.getQueueConfigId())
                    .orElseThrow(() -> new BusinessException(503, "Không xác định được phòng của phiếu."));
            queues.roomDashboard(config.getRoomCode(), actor, role);
        }
        List<Entry> response = selected.stream()
                .map(entry -> new Entry(
                        entry.getId(),
                        entry.getPatientId(),
                        entry.getConsultationPhase().name(),
                        entry.getStatus().name()))
                .toList();
        return ApiResponse.success(response);
    }
}
