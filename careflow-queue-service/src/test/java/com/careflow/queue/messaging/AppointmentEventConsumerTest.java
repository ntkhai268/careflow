package com.careflow.queue.messaging;

import com.careflow.common.event.EventEnvelope;
import com.careflow.common.exception.BusinessException;
import com.careflow.queue.domain.QueueConfig;
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
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AppointmentEventConsumerTest {
    @Mock ProcessedEventRepository processedEvents;
    @Mock QueueEntryRepository entries;
    @Mock QueueManagementService queueService;
    @Mock QueueEventService events;

    private AppointmentEventConsumer consumer;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        consumer = new AppointmentEventConsumer(processedEvents, entries, queueService, events);
    }

    @Test
    void appointmentCreatedRequiresAndPropagatesTimeSlotStart() {
        UUID appointmentId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        LocalDate date = LocalDate.of(2026, 7, 28);
        ObjectNode payload = payload(appointmentId, patientId, userId, departmentId, date, "08:00-08:30");
        QueueConfig config = new QueueConfig();
        QueueEntry entry = new QueueEntry();
        entry.setId(UUID.randomUUID());
        entry.setScheduledStartAt(Instant.parse("2026-07-28T01:00:00Z"));
        when(queueService.requireLockedConfig(departmentId)).thenReturn(config);
        when(entries.findByAppointmentId(appointmentId)).thenReturn(Optional.empty());
        when(queueService.createAppointmentEntry(
                config, date, LocalTime.of(8, 0), appointmentId, patientId, userId)).thenReturn(entry);

        consumer.consume(envelope(appointmentId, payload));

        verify(queueService).createAppointmentEntry(
                config, date, LocalTime.of(8, 0), appointmentId, patientId, userId);
        verify(entries).saveAndFlush(entry);
        verify(events).append(eq(entry), eq(config), eq("QUEUE_NUMBER_ASSIGNED"), anyString(),
                eq("trace-1"), argThat(extra ->
                        "2026-07-28T01:00:00Z".equals(extra.get("scheduledStartAt"))));
    }

    @Test
    void rejectsAppointmentWithoutValidTimeSlot() {
        UUID appointmentId = UUID.randomUUID();
        ObjectNode payload = payload(
                appointmentId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                LocalDate.of(2026, 7, 28), "08:00");

        assertThatThrownBy(() -> consumer.consume(envelope(appointmentId, payload)))
                .isInstanceOf(BusinessException.class)
                .extracting("status").isEqualTo(422);
        verifyNoInteractions(queueService, events);
    }

    private ObjectNode payload(UUID appointmentId, UUID patientId, UUID userId,
                               UUID departmentId, LocalDate date, String timeSlot) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("appointmentId", appointmentId.toString());
        payload.put("patientId", patientId.toString());
        payload.put("userId", userId.toString());
        payload.put("departmentId", departmentId.toString());
        payload.put("appointmentDate", date.toString());
        payload.put("timeSlot", timeSlot);
        payload.put("priorityLevel", "APPOINTMENT");
        return payload;
    }

    private EventEnvelope envelope(UUID aggregateId, ObjectNode payload) {
        return new EventEnvelope(
                UUID.randomUUID(), "APPOINTMENT_CREATED", 1, aggregateId, 1,
                Instant.now(), "appointment-service", "trace-1", payload);
    }
}
