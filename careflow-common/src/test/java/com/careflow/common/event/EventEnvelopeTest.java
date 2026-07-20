package com.careflow.common.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class EventEnvelopeTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void factoryCreatesRequiredMetadata() {
        UUID aggregateId = UUID.randomUUID();
        EventEnvelope event = EventEnvelope.create(
                "AppointmentCreated",
                1,
                aggregateId,
                0,
                "appointment-service",
                "correlation-1",
                objectMapper.createObjectNode().put("appointmentId", aggregateId.toString()));

        assertThat(event.eventId()).isNotNull();
        assertThat(event.occurredAt()).isNotNull();
        assertThat(event.aggregateId()).isEqualTo(aggregateId);
    }

    @Test
    void invalidEventCannotBeCreated() {
        assertThatNullPointerException().isThrownBy(() -> new EventEnvelope(
                null, "Type", 1, UUID.randomUUID(), 0,
                java.time.Instant.now(), "producer", "correlation", objectMapper.createObjectNode()));
        assertThatIllegalArgumentException().isThrownBy(() -> EventEnvelope.create(
                " ", 0, UUID.randomUUID(), -1,
                "producer", "correlation", objectMapper.createObjectNode()));
    }
}
