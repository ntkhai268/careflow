package com.careflow.queue.messaging;

import com.careflow.common.event.EventEnvelope;
import com.careflow.queue.domain.QueueEntry;
import com.careflow.queue.repository.ProcessedEventRepository;
import com.careflow.queue.repository.QueueEntryRepository;
import com.careflow.queue.service.QueueEventService;
import com.careflow.queue.service.QueueManagementService;
import com.fasterxml.jackson.databind.ObjectMapper;
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
class PrescriptionEventConsumerTest {
    @Mock ProcessedEventRepository processedEvents;
    @Mock QueueEntryRepository entries;
    @Mock QueueManagementService queueService;
    @Mock QueueEventService events;

    private PrescriptionEventConsumer consumer;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        consumer = new PrescriptionEventConsumer(processedEvents, entries, queueService, events);
    }

    @Test
    void issuedPrescriptionCreatesExactlyOnePharmacyQueueEntry() {
        UUID prescriptionId = UUID.randomUUID();
        UUID consultationId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        Instant issuedAt = Instant.parse("2026-08-18T05:05:00Z");
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("prescriptionId", prescriptionId.toString());
        payload.put("consultationId", consultationId.toString());
        payload.put("patientId", patientId.toString());
        payload.put("dispensingServicePointId", "PHARMACY-01");
        payload.put("issuedAt", issuedAt.toString());
        QueueEntry entry = new QueueEntry();
        entry.setId(UUID.randomUUID());
        when(entries.findByPrescriptionId(prescriptionId)).thenReturn(Optional.empty());
        when(queueService.createPharmacyEntry(
                prescriptionId, consultationId, patientId, "PHARMACY-01", issuedAt)).thenReturn(entry);

        consumer.consume(envelope("PrescriptionIssued", prescriptionId, payload));

        verify(entries).saveAndFlush(entry);
        verify(events).append(eq(entry), isNull(), eq("QueueEntryCreated"), eq("queue.created"),
                eq("trace-1"), anyMap());
    }

    @Test
    void redeliveredEventDoesNothing() {
        UUID prescriptionId = UUID.randomUUID();
        ObjectNode payload = objectMapper.createObjectNode();
        EventEnvelope envelope = envelope("PrescriptionIssued", prescriptionId, payload);
        when(processedEvents.existsById(any())).thenReturn(true);

        consumer.consume(envelope);

        verifyNoInteractions(entries, queueService, events);
    }

    @Test
    void dispensedPrescriptionCompletesMatchingQueueEntry() {
        UUID prescriptionId = UUID.randomUUID();
        Instant dispensedAt = Instant.parse("2026-08-18T05:15:00Z");
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("prescriptionId", prescriptionId.toString());
        payload.put("dispensedAt", dispensedAt.toString());

        consumer.consume(envelope("PrescriptionDispensed", prescriptionId, payload));

        verify(queueService).completePharmacyEntry(prescriptionId, dispensedAt, "trace-1");
    }

    private EventEnvelope envelope(String type, UUID aggregateId, ObjectNode payload) {
        return new EventEnvelope(UUID.randomUUID(), type, 1, aggregateId, 1,
                Instant.parse("2026-08-18T05:05:00Z"), "prescription-service", "trace-1", payload);
    }
}
