package com.careflow.queue.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "queue_entries", schema = "queue")
@Getter @Setter @NoArgsConstructor
public class QueueEntry {
    @Id private UUID id;
    @Column(name = "queue_config_id", nullable = false) private UUID queueConfigId;
    @Column(name = "department_id", nullable = false) private UUID departmentId;
    @Column(name = "appointment_id", unique = true) private UUID appointmentId;
    @Column(name = "patient_id", nullable = false) private UUID patientId;
    @Column(name = "user_id", nullable = false) private UUID userId;
    @Column(name = "queue_date", nullable = false) private LocalDate queueDate;
    @Column(name = "sequence_number", nullable = false) private int sequenceNumber;
    @Column(name = "queue_number", nullable = false, length = 20) private String queueNumber;
    @Enumerated(EnumType.STRING) @Column(name = "priority_level", nullable = false) private PriorityLevel priorityLevel;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private QueueStatus status = QueueStatus.WAITING;
    @Column(name = "checked_in_at") private Instant checkedInAt;
    @Column(name = "eligible_since_at") private Instant eligibleSinceAt;
    @Column(name = "called_at") private Instant calledAt;
    @Column(name = "started_at") private Instant startedAt;
    @Column(name = "completed_at") private Instant completedAt;
    @Column(name = "missed_at") private Instant missedAt;
    @Column(name = "cancelled_at") private Instant cancelledAt;
    @Column(name = "missed_count", nullable = false, columnDefinition = "smallint") private int missedCount;
    @Column(name = "estimated_wait_minutes") private Integer estimatedWaitMinutes;
    @Version @Column(nullable = false) private long version;
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @UpdateTimestamp @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @PrePersist void assignId() { if (id == null) id = UUID.randomUUID(); }
}
