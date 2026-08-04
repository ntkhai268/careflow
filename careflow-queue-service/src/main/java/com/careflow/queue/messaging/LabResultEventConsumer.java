package com.careflow.queue.messaging;

import com.careflow.common.event.EventEnvelope;
import com.careflow.common.exception.BusinessException;
import com.careflow.queue.config.RabbitMqConfig;
import com.careflow.queue.domain.*;
import com.careflow.queue.repository.ProcessedEventRepository;
import com.careflow.queue.repository.QueueEntryRepository;
import com.careflow.queue.service.QueueManagementService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Component
public class LabResultEventConsumer {
    private static final String CONSUMER = "queue-service.lab-result-events";

    private final ProcessedEventRepository processedEvents;
    private final QueueEntryRepository entries;
    private final QueueManagementService queueService;

    public LabResultEventConsumer(ProcessedEventRepository processedEvents,
                                  QueueEntryRepository entries,
                                  QueueManagementService queueService) {
        this.processedEvents = processedEvents;
        this.entries = entries;
        this.queueService = queueService;
    }

    @RabbitListener(queues = RabbitMqConfig.LAB_RESULTS_QUEUE)
    @Transactional
    public void consume(EventEnvelope envelope) {
        validateEnvelope(envelope);
        ProcessedEventId eventId = new ProcessedEventId(envelope.eventId(), CONSUMER);
        if (processedEvents.existsById(eventId)) return;

        JsonNode payload = envelope.payload();
        UUID consultationId = requiredUuid(payload, "consultationId");
        if (entries.findByConsultationIdAndQueueTypeAndConsultationPhase(
                consultationId, QueueType.CONSULTATION, ConsultationPhase.RESULT_REVIEW).isEmpty()) {
            QueueEntry review = queueService.createResultReviewEntry(
                    consultationId,
                    requiredUuid(payload, "patientId"),
                    optionalUuid(payload, "sourceQueueEntryId"),
                    envelope.occurredAt());
            entries.saveAndFlush(review);
        }

        ProcessedEvent processed = new ProcessedEvent();
        processed.setId(eventId);
        processed.setEventType(envelope.eventType());
        processed.setProcessedAt(Instant.now());
        processedEvents.save(processed);
    }

    private void validateEnvelope(EventEnvelope envelope) {
        if (envelope == null || envelope.eventId() == null || envelope.aggregateId() == null
                || envelope.occurredAt() == null || envelope.eventVersion() != 1
                || envelope.payload() == null
                || !"AllRequiredResultsAvailable".equals(envelope.eventType())) {
            throw new BusinessException(422, "Event AllRequiredResultsAvailable không hợp lệ");
        }
    }

    private UUID requiredUuid(JsonNode payload, String field) {
        if (!payload.hasNonNull(field) || payload.get(field).asText().isBlank()) {
            throw new BusinessException(422, "Thiếu field bắt buộc: " + field);
        }
        try { return UUID.fromString(payload.get(field).asText()); }
        catch (IllegalArgumentException exception) {
            throw new BusinessException(422, field + " không phải UUID hợp lệ");
        }
    }

    private UUID optionalUuid(JsonNode payload, String field) {
        if (!payload.hasNonNull(field) || payload.get(field).asText().isBlank()) return null;
        try { return UUID.fromString(payload.get(field).asText()); }
        catch (IllegalArgumentException exception) {
            throw new BusinessException(422, field + " không phải UUID hợp lệ");
        }
    }
}
