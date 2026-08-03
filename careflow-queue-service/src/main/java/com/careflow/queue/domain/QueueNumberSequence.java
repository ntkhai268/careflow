package com.careflow.queue.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "queue_number_sequences", schema = "queue",
        uniqueConstraints = @UniqueConstraint(name = "uk_queue_number_sequence", columnNames = {"queue_config_id", "queue_date"}))
@Getter @Setter @NoArgsConstructor
public class QueueNumberSequence {
    @Id private UUID id;
    @Column(name = "queue_config_id", nullable = false) private UUID queueConfigId;
    @Column(name = "queue_date", nullable = false) private LocalDate queueDate;
    @Column(name = "last_number", nullable = false) private int lastNumber;
    @Version @Column(nullable = false) private long version;
    @UpdateTimestamp @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @PrePersist void assignId() { if (id == null) id = UUID.randomUUID(); }
}
