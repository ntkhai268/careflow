package com.careflow.queue.controller;

import com.careflow.common.dto.ApiResponse;
import com.careflow.common.exception.BusinessException;
import com.careflow.common.exception.ResourceNotFoundException;
import com.careflow.common.security.PatientOwnershipClient;
import com.careflow.queue.domain.QueueConfig;
import com.careflow.queue.domain.QueueEntry;
import com.careflow.queue.repository.QueueConfigRepository;
import com.careflow.queue.repository.QueueEntryRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
public class PatientQueueEntryController {
    private final QueueEntryRepository entries;
    private final QueueConfigRepository configs;
    private final PatientOwnershipClient owners;

    public PatientQueueEntryController(QueueEntryRepository entries,
                                       QueueConfigRepository configs,
                                       PatientOwnershipClient owners) {
        this.entries = entries;
        this.configs = configs;
        this.owners = owners;
    }

    public record Context(
            UUID entryId,
            UUID patientId,
            UUID appointmentId,
            UUID consultationId,
            String queueNumber,
            String status,
            String phase,
            String roomId
    ) {}

    @GetMapping("/api/queues/entries/{id}/patient-context")
    public ApiResponse<Context> get(@PathVariable UUID id,
                                    @RequestHeader("X-User-Id") UUID actor,
                                    @RequestHeader("X-User-Role") String role) {
        if (!"PATIENT".equals(role)) {
            throw new BusinessException(403, "Chỉ bệnh nhân được xem phiếu cá nhân.");
        }
        QueueEntry entry = entries.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("QueueEntry", "id", id));
        owners.require(entry.getPatientId(), actor);
        QueueConfig config = entry.getQueueConfigId() == null
                ? null
                : configs.findById(entry.getQueueConfigId()).orElse(null);
        String phase = entry.getConsultationPhase() == null ? null : entry.getConsultationPhase().name();
        String roomId = config == null ? entry.getServicePointId() : config.getRoomCode();
        return ApiResponse.success(new Context(
                entry.getId(),
                entry.getPatientId(),
                entry.getAppointmentId(),
                entry.getConsultationId(),
                entry.getQueueNumber(),
                entry.getStatus().name(),
                phase,
                roomId));
    }
}
