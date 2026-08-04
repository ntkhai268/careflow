package com.careflow.queue.messaging;

import com.careflow.common.event.EventEnvelope;
import com.careflow.queue.domain.*;
import com.careflow.queue.repository.ProcessedEventRepository;
import com.careflow.queue.repository.QueueEntryRepository;
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

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LabResultEventConsumerTest {
    @Mock ProcessedEventRepository processedEvents;
    @Mock QueueEntryRepository entries;
    @Mock QueueManagementService queueService;

    private LabResultEventConsumer consumer;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        consumer = new LabResultEventConsumer(processedEvents, entries, queueService);
    }

    @Test
    void allRequiredResultsAutomaticallyCreatesActiveResultReviewEntry() {
        UUID consultationId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        UUID sourceQueueEntryId = UUID.randomUUID();
        Instant readyAt = Instant.parse("2026-08-18T05:00:00Z");
        ObjectNode payload = payload(consultationId, patientId, sourceQueueEntryId);
        QueueEntry review = new QueueEntry();
        review.setId(UUID.randomUUID());
        when(entries.findByConsultationIdAndQueueTypeAndConsultationPhase(
                consultationId, QueueType.CONSULTATION, ConsultationPhase.RESULT_REVIEW))
                .thenReturn(Optional.empty());
        when(queueService.createResultReviewEntry(
                consultationId, patientId, sourceQueueEntryId, readyAt)).thenReturn(review);

        consumer.consume(envelope(consultationId, readyAt, payload));

        verify(entries).saveAndFlush(review);
        verify(processedEvents).save(any(ProcessedEvent.class));
    }

    @Test
    void secondBusinessEventForSameConsultationDoesNotCreateDuplicate() {
        UUID consultationId = UUID.randomUUID();
        QueueEntry existing = new QueueEntry();
        existing.setId(UUID.randomUUID());
        when(entries.findByConsultationIdAndQueueTypeAndConsultationPhase(
                consultationId, QueueType.CONSULTATION, ConsultationPhase.RESULT_REVIEW))
                .thenReturn(Optional.of(existing));

        consumer.consume(envelope(consultationId, Instant.now(),
                payload(consultationId, UUID.randomUUID(), null)));

        verifyNoInteractions(queueService);
        verify(entries, never()).saveAndFlush(any());
        verify(processedEvents).save(any(ProcessedEvent.class));
    }

    @Test
    void redeliveredEventIdIsIgnored() {
        UUID consultationId = UUID.randomUUID();
        when(processedEvents.existsById(any())).thenReturn(true);

        consumer.consume(envelope(consultationId, Instant.now(), objectMapper.createObjectNode()));

        verifyNoInteractions(entries, queueService);
        verify(processedEvents, never()).save(any());
    }

    private ObjectNode payload(UUID consultationId, UUID patientId, UUID sourceQueueEntryId) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("consultationId", consultationId.toString());
        payload.put("patientId", patientId.toString());
        if (sourceQueueEntryId != null) payload.put("sourceQueueEntryId", sourceQueueEntryId.toString());
        return payload;
    }

    private EventEnvelope envelope(UUID aggregateId, Instant occurredAt, ObjectNode payload) {
        return new EventEnvelope(UUID.randomUUID(), "AllRequiredResultsAvailable", 1,
                aggregateId, 1, occurredAt, "lab-service", "trace-1", payload);
    }
}
