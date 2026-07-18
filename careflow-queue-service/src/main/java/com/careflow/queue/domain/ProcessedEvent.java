package com.careflow.queue.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "processed_events", schema = "queue")
@Getter @Setter @NoArgsConstructor
public class ProcessedEvent {
    @EmbeddedId private ProcessedEventId id;
    @Column(name = "event_type", nullable = false, length = 100) private String eventType;
    @Column(name = "processed_at", nullable = false) private Instant processedAt;
}
