package com.careflow.queue.messaging;

import com.careflow.common.event.EventEnvelope;
import com.careflow.common.exception.BusinessException;
import com.careflow.queue.config.RabbitMqConfig;
import com.careflow.queue.domain.ProcessedEvent;
import com.careflow.queue.domain.ProcessedEventId;
import com.careflow.queue.domain.QueueEntry;
import com.careflow.queue.repository.ProcessedEventRepository;
import com.careflow.queue.repository.QueueEntryRepository;
import com.careflow.queue.service.QueueEventService;
import com.careflow.queue.service.QueueManagementService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Component
public class LabOrderReadyEventConsumer {
    private static final String CONSUMER = "queue-service.lab-ready-events";

    private final ProcessedEventRepository processedEvents;
    private final QueueEntryRepository entries;
    private final QueueManagementService queueService;
    private final QueueEventService events;

    public LabOrderReadyEventConsumer(ProcessedEventRepository processedEvents,
                                      QueueEntryRepository entries,
                                      QueueManagementService queueService,
                                      QueueEventService events) {
        this.processedEvents = processedEvents;
        this.entries = entries;
        this.queueService = queueService;
        this.events = events;
    }

    @RabbitListener(queues = RabbitMqConfig.LAB_READY_QUEUE)
    @Transactional
    public void consume(EventEnvelope envelope) {
        validateEnvelope(envelope);
        ProcessedEventId eventId = new ProcessedEventId(envelope.eventId(), CONSUMER);
        if (processedEvents.existsById(eventId)) return;

        JsonNode payload = envelope.payload();
        UUID labOrderId = payload.hasNonNull("orderId")
                ? requiredUuid(payload, "orderId")
                : requiredUuid(payload, "labOrderId");
        UUID consultationId = requiredUuid(payload, "consultationId");
        UUID patientId = requiredUuid(payload, "patientId");
        JsonNode servicePoints = payload.get("servicePoints");
        Set<String> seen = new HashSet<>();
        for (JsonNode servicePoint : servicePoints) {
            String servicePointId = requiredText(servicePoint, "servicePointId").toUpperCase();
            if (!seen.add(servicePointId)) continue;
            if (entries.findByLabOrderIdAndServicePointId(labOrderId, servicePointId).isPresent()) continue;
            QueueEntry entry = queueService.createLabExecutionEntry(
                    labOrderId, consultationId, patientId, servicePointId, envelope.occurredAt());
            entries.saveAndFlush(entry);
            events.append(entry, null, "QueueEntryCreated", "queue.created", envelope.correlationId(),
                    Map.of("labOrderId", labOrderId,
                            "consultationId", consultationId,
                            "queuedAt", envelope.occurredAt().toString()));
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
                || !"LabOrderReadyForExecution".equals(envelope.eventType())) {
            throw new BusinessException(422, "Event LabOrderReadyForExecution không hợp lệ");
        }
        JsonNode servicePoints = envelope.payload().get("servicePoints");
        if (servicePoints == null || !servicePoints.isArray() || servicePoints.isEmpty()) {
            throw new BusinessException(422, "Thiếu servicePoints cho LabOrderReadyForExecution");
        }
    }

    private UUID requiredUuid(JsonNode payload, String field) {
        try { return UUID.fromString(requiredText(payload, field)); }
        catch (IllegalArgumentException exception) {
            throw new BusinessException(422, field + " không phải UUID hợp lệ");
        }
    }

    private String requiredText(JsonNode payload, String field) {
        if (!payload.hasNonNull(field) || payload.get(field).asText().isBlank()) {
            throw new BusinessException(422, "Thiếu field bắt buộc: " + field);
        }
        return payload.get(field).asText().trim();
    }
}
