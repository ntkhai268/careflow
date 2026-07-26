package com.careflow.common.event;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
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
}
