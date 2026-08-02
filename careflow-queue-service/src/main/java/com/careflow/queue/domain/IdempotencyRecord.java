package com.careflow.queue.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "idempotency_records", schema = "queue",
        uniqueConstraints = @UniqueConstraint(name = "uk_queue_idempotency",
                columnNames = {"command_name", "scope_id", "idempotency_key"}))
@Getter
@Setter
@NoArgsConstructor
public class IdempotencyRecord {
    @Id
    private UUID id;

    @Column(name = "command_name", nullable = false, length = 50)
    private String commandName;

    @Column(name = "scope_id", nullable = false)
    private UUID scopeId;

    @Column(name = "idempotency_key", nullable = false, length = 100)
    private String idempotencyKey;

    @Column(name = "request_fingerprint", nullable = false, length = 64)
    private String requestFingerprint;

    @Column(name = "result_entry_id")
    private UUID resultEntryId;

    @Column(name = "empty_result", nullable = false)
    private boolean emptyResult;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void assignId() {
        if (id == null) id = UUID.randomUUID();
    }
}
