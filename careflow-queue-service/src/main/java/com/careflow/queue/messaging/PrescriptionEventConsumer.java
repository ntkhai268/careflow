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
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.UUID;

@Component
public class PrescriptionEventConsumer {
    private static final String CONSUMER = "queue-service.prescription-events";

    private final ProcessedEventRepository processedEvents;
    private final QueueEntryRepository entries;
    private final QueueManagementService queueService;
    private final QueueEventService events;

    public PrescriptionEventConsumer(ProcessedEventRepository processedEvents, QueueEntryRepository entries,
                                     QueueManagementService queueService, QueueEventService events) {
        this.processedEvents = processedEvents;
        this.entries = entries;
        this.queueService = queueService;
        this.events = events;
    }

    @RabbitListener(queues = RabbitMqConfig.PRESCRIPTION_QUEUE)
    @Transactional
    public void consume(EventEnvelope envelope) {
        validateEnvelope(envelope);
        ProcessedEventId eventId = new ProcessedEventId(envelope.eventId(), CONSUMER);
        if (processedEvents.existsById(eventId)) return;
        switch (envelope.eventType()) {
            case "PrescriptionIssued" -> issue(envelope);
            case "PrescriptionCancelled" -> queueService.cancelPharmacyEntry(
                    requiredUuid(envelope.payload(), "prescriptionId"), envelope.correlationId());
            case "PrescriptionDispensed" -> queueService.completePharmacyEntry(
                    requiredUuid(envelope.payload(), "prescriptionId"),
                    optionalInstant(envelope.payload(), "dispensedAt", envelope.occurredAt()),
                    envelope.correlationId());
            default -> throw new BusinessException(422,
                    "Event type không được hỗ trợ: " + envelope.eventType());
        }
        ProcessedEvent processed = new ProcessedEvent();
        processed.setId(eventId);
        processed.setEventType(envelope.eventType());
        processed.setProcessedAt(Instant.now());
        processedEvents.save(processed);
    }

    private void issue(EventEnvelope envelope) {
        JsonNode payload = envelope.payload();
        UUID prescriptionId = requiredUuid(payload, "prescriptionId");
        if (entries.findByPrescriptionId(prescriptionId).isPresent()) return;
        Instant issuedAt = optionalInstant(payload, "issuedAt", envelope.occurredAt());
        QueueEntry entry = queueService.createPharmacyEntry(
                prescriptionId,
                requiredUuid(payload, "consultationId"),
                requiredUuid(payload, "patientId"),
                requiredText(payload, "dispensingServicePointId"),
                issuedAt);
        entries.saveAndFlush(entry);
        events.append(entry, null, "QueueEntryCreated", "queue.created", envelope.correlationId(),
                Map.of("prescriptionId", prescriptionId, "queuedAt", issuedAt.toString()));
    }

    private void validateEnvelope(EventEnvelope envelope) {
        if (envelope == null || envelope.eventId() == null || envelope.aggregateId() == null
                || envelope.eventVersion() != 1 || envelope.payload() == null) {
            throw new BusinessException(422, "Event envelope không hợp lệ");
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
        return payload.get(field).asText();
    }

    private Instant optionalInstant(JsonNode payload, String field, Instant fallback) {
        if (!payload.hasNonNull(field) || payload.get(field).asText().isBlank()) return fallback;
        try { return Instant.parse(payload.get(field).asText()); }
        catch (DateTimeParseException exception) {
            throw new BusinessException(422, field + " không phải timestamp hợp lệ");
        }
    }
}
