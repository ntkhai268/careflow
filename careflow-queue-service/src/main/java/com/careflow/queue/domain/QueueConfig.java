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
@Table(name = "queue_configs", schema = "queue")
@Getter @Setter @NoArgsConstructor
public class QueueConfig {
    @Id private UUID id;
    @Column(name = "department_id", nullable = false, unique = true) private UUID departmentId;
    @Column(name = "department_name_snapshot", nullable = false, length = 100) private String departmentNameSnapshot;
    @Column(name = "queue_prefix", nullable = false, length = 10) private String queuePrefix;
    @Column(name = "room_code", length = 50) private String roomCode;
    @Column(name = "priority_ratio_n", nullable = false, columnDefinition = "smallint") private int priorityRatioN = 2;
    @Column(name = "normal_ratio_m", nullable = false, columnDefinition = "smallint") private int normalRatioM = 1;
    @Column(name = "avg_consultation_minutes", nullable = false, columnDefinition = "smallint") private int avgConsultationMinutes = 15;
    @Column(name = "near_turn_threshold", nullable = false, columnDefinition = "smallint") private int nearTurnThreshold = 3;
    @Enumerated(EnumType.STRING) @Column(name = "missed_policy", nullable = false) private MissedPolicy missedPolicy = MissedPolicy.REQUEUE_BACK;
    @Column(name = "scheduler_date") private LocalDate schedulerDate;
    @Enumerated(EnumType.STRING) @Column(name = "cycle_phase", nullable = false) private CyclePhase cyclePhase = CyclePhase.PRIORITY;
    @Column(name = "served_in_phase", nullable = false, columnDefinition = "smallint") private int servedInPhase;
    @Enumerated(EnumType.STRING) @Column(name = "normal_cursor", nullable = false) private NormalCursor normalCursor = NormalCursor.APPOINTMENT;
    @Column(name = "is_active", nullable = false) private boolean active = true;
    @Version @Column(nullable = false) private long version;
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @UpdateTimestamp @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    @PrePersist void assignId() { if (id == null) id = UUID.randomUUID(); }

    public void resetScheduler(LocalDate date) {
        schedulerDate = date;
        cyclePhase = CyclePhase.PRIORITY;
        servedInPhase = 0;
        normalCursor = NormalCursor.APPOINTMENT;
    }
}
