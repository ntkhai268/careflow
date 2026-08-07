package com.careflow.queue.messaging;

import com.careflow.common.event.EventEnvelope;
import com.careflow.queue.domain.ProcessedEvent;
import com.careflow.queue.domain.ProcessedEventId;
import com.careflow.queue.domain.QueueEntry;
import com.careflow.queue.repository.ProcessedEventRepository;
import com.careflow.queue.repository.QueueEntryRepository;
import com.careflow.queue.service.QueueEventService;
import com.careflow.queue.service.QueueManagementService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LabOrderReadyEventConsumerTest {
    @Mock ProcessedEventRepository processedEvents;
    @Mock QueueEntryRepository entries;
    @Mock QueueManagementService queueService;
    @Mock QueueEventService events;

    private LabOrderReadyEventConsumer consumer;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        consumer = new LabOrderReadyEventConsumer(processedEvents, entries, queueService, events);
    }

    @Test
    void readyOrderCreatesOneQueuedLabExecutionEntryPerServicePoint() {
        UUID labOrderId = UUID.randomUUID();
        UUID consultationId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        Instant orderedAt = Instant.parse("2026-08-18T05:00:00Z");
        QueueEntry labQueue = new QueueEntry();
        labQueue.setId(UUID.randomUUID());
        when(entries.findByLabOrderIdAndServicePointId(labOrderId, "LAB-HEMATOLOGY-01"))
                .thenReturn(Optional.empty());
        when(queueService.createLabExecutionEntry(
                labOrderId, consultationId, patientId, "LAB-HEMATOLOGY-01", orderedAt))
                .thenReturn(labQueue);

        consumer.consume(envelope(labOrderId, orderedAt, payload(labOrderId, consultationId, patientId)));

        verify(entries).saveAndFlush(labQueue);
        verify(events).append(eq(labQueue), isNull(), eq("QueueEntryCreated"), eq("queue.created"),
                eq("trace-ready-1"), argThat(extra -> labOrderId.equals(extra.get("labOrderId"))));
        verify(processedEvents).save(any(ProcessedEvent.class));
    }

    @Test
    void redeliveredReadyEventIdDoesNothing() {
        EventEnvelope envelope = envelope(UUID.randomUUID(), Instant.now(),
                payload(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()));
        when(processedEvents.existsById(any(ProcessedEventId.class))).thenReturn(true);

        consumer.consume(envelope);

        verifyNoInteractions(entries, queueService, events);
        verify(processedEvents, never()).save(any());
    }

    @Test
    void secondBusinessEventForSameOrderAndServicePointDoesNotDuplicateLabQueueEntry() {
        UUID labOrderId = UUID.randomUUID();
        QueueEntry existing = new QueueEntry();
        existing.setId(UUID.randomUUID());
        when(entries.findByLabOrderIdAndServicePointId(labOrderId, "LAB-HEMATOLOGY-01"))
                .thenReturn(Optional.of(existing));

        consumer.consume(envelope(labOrderId, Instant.now(),
                payload(labOrderId, UUID.randomUUID(), UUID.randomUUID())));

        verifyNoInteractions(queueService);
        verify(entries, never()).saveAndFlush(any());
        verify(processedEvents).save(any(ProcessedEvent.class));
    }

    private ObjectNode payload(UUID labOrderId, UUID consultationId, UUID patientId) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("labOrderId", labOrderId.toString());
        payload.put("consultationId", consultationId.toString());
        payload.put("patientId", patientId.toString());
        ArrayNode servicePoints = payload.putArray("servicePoints");
        servicePoints.addObject()
                .put("servicePointId", "LAB-HEMATOLOGY-01")
                .put("serviceName", "Complete blood count");
        return payload;
    }

    private EventEnvelope envelope(UUID aggregateId, Instant occurredAt, ObjectNode payload) {
        return new EventEnvelope(UUID.randomUUID(), "LabOrderReadyForExecution", 1,
                aggregateId, 1, occurredAt, "lab-service", "trace-ready-1", payload);
    }
}
