package com.careflow.queue.service;

import com.careflow.common.constants.AppConstants;
import com.careflow.common.event.EventEnvelope;
import com.careflow.queue.domain.*;
import com.careflow.queue.repository.OutboxEventRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class QueueEventService {
    private final OutboxEventRepository outbox;
    private final ObjectMapper objectMapper;

    public QueueEventService(OutboxEventRepository outbox, ObjectMapper objectMapper) {
        this.outbox = outbox;
        this.objectMapper = objectMapper;
    }

    public void append(QueueEntry entry, QueueConfig config, String eventType, String routingKey,
                       String correlationId, Map<String, Object> extra) {
        UUID eventId = UUID.randomUUID();
        Instant now = Instant.now();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("entryId", entry.getId());
        body.put("appointmentId", entry.getAppointmentId());
        body.put("patientId", entry.getPatientId());
        body.put("recipientUserId", entry.getUserId());
        body.put("departmentId", entry.getDepartmentId());
        body.put("queueNumber", entry.getQueueNumber());
        body.put("roomId", config.getRoomCode());
        body.putAll(extra);
        EventEnvelope envelope = new EventEnvelope(eventId, eventType, 1, entry.getId(), entry.getVersion(),
                now, "queue-service", correlationId == null ? eventId.toString() : correlationId,
                objectMapper.valueToTree(body));

        OutboxEvent event = new OutboxEvent();
        event.setEventId(eventId);
        event.setAggregateType("QueueEntry");
        event.setAggregateId(entry.getId());
        event.setEventType(eventType);
        event.setEventVersion(1);
        event.setAggregateVersion(entry.getVersion());
        event.setExchangeName(AppConstants.EXCHANGE_QUEUE);
        event.setRoutingKey(routingKey);
        event.setPayload(objectMapper.valueToTree(envelope));
        event.setStatus(DeliveryStatus.PENDING);
        event.setOccurredAt(now);
        event.setNextAttemptAt(now);
        event.setCreatedAt(now);
        outbox.save(event);
    }
}
