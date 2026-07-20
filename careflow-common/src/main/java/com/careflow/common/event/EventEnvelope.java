package com.careflow.common.event;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record EventEnvelope(
        UUID eventId,
        String eventType,
        int eventVersion,
        UUID aggregateId,
        long aggregateVersion,
        Instant occurredAt,
        String producer,
        String correlationId,
        JsonNode payload
) {
    public EventEnvelope {
        eventId = Objects.requireNonNull(eventId, "eventId must not be null");
        eventType = requireText(eventType, "eventType");
        if (eventVersion < 1) {
            throw new IllegalArgumentException("eventVersion must be at least 1");
        }
        aggregateId = Objects.requireNonNull(aggregateId, "aggregateId must not be null");
        if (aggregateVersion < 0) {
            throw new IllegalArgumentException("aggregateVersion must not be negative");
        }
        occurredAt = Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        producer = requireText(producer, "producer");
        correlationId = requireText(correlationId, "correlationId");
        payload = Objects.requireNonNull(payload, "payload must not be null");
    }

    public static EventEnvelope create(
            String eventType,
            int eventVersion,
            UUID aggregateId,
            long aggregateVersion,
            String producer,
            String correlationId,
            JsonNode payload) {
        return new EventEnvelope(
                UUID.randomUUID(),
                eventType,
                eventVersion,
                aggregateId,
                aggregateVersion,
                Instant.now(),
                producer,
                correlationId,
                payload);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
