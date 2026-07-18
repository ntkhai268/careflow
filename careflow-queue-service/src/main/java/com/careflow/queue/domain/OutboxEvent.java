package com.careflow.queue.domain;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "outbox_events", schema = "queue")
@Getter @Setter @NoArgsConstructor
public class OutboxEvent {
    @Id @Column(name = "event_id") private UUID eventId;
    @Column(name = "aggregate_type", nullable = false, length = 50) private String aggregateType;
    @Column(name = "aggregate_id", nullable = false) private UUID aggregateId;
    @Column(name = "event_type", nullable = false, length = 100) private String eventType;
    @Column(name = "event_version", nullable = false) private int eventVersion;
    @Column(name = "aggregate_version", nullable = false) private long aggregateVersion;
    @Column(name = "exchange_name", nullable = false, length = 100) private String exchangeName;
    @Column(name = "routing_key", nullable = false, length = 100) private String routingKey;
    @JdbcTypeCode(SqlTypes.JSON) @Column(nullable = false, columnDefinition = "jsonb") private JsonNode payload;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private DeliveryStatus status = DeliveryStatus.PENDING;
    @Column(nullable = false) private int attempts;
    @Column(name = "occurred_at", nullable = false) private Instant occurredAt;
    @Column(name = "next_attempt_at", nullable = false) private Instant nextAttemptAt;
    @Column(name = "published_at") private Instant publishedAt;
    @Column(name = "last_error") private String lastError;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
}
