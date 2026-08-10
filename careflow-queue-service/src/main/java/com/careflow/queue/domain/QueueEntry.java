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
    @Column(name = "queue_config_id") private UUID queueConfigId;
    @Column(name = "department_id") private UUID departmentId;
    @Column(name = "department_code", length = 50) private String departmentCode;
    @Column(name = "appointment_id", unique = true) private UUID appointmentId;
    @Column(name = "consultation_id") private UUID consultationId;
    @Column(name = "lab_order_id") private UUID labOrderId;
    @Column(name = "prescription_id") private UUID prescriptionId;
    @Column(name = "patient_id", nullable = false) private UUID patientId;
    @Column(name = "user_id") private UUID userId;
    @Enumerated(EnumType.STRING) @Column(name = "queue_type", nullable = false) private QueueType queueType = QueueType.CONSULTATION;
    @Enumerated(EnumType.STRING) @Column(name = "consultation_phase") private ConsultationPhase consultationPhase = ConsultationPhase.INITIAL;
    @Enumerated(EnumType.STRING) @Column(name = "queue_class") private QueueClass queueClass = QueueClass.NORMAL;
    @Column(name = "service_point_id", length = 80) private String servicePointId;
    @Column(name = "queue_date", nullable = false) private LocalDate queueDate;
    @Column(name = "sequence_number", nullable = false) private int sequenceNumber;
    @Column(name = "queue_number", nullable = false, length = 20) private String queueNumber;
    @Enumerated(EnumType.STRING) @Column(name = "priority_level", nullable = false) private PriorityLevel priorityLevel;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private QueueStatus status = QueueStatus.WAITING;
    @Column(name = "scheduled_start_at") private Instant scheduledStartAt;
    @Column(name = "time_slot", length = 20) private String timeSlot;
    @Column(name = "room_display_name_snapshot", length = 150) private String roomDisplayNameSnapshot;
    @Column(name = "checked_in_at") private Instant checkedInAt;
    @Column(name = "checked_in_by_user_id") private UUID checkedInByUserId;
    @Column(name = "priority_reason_code", length = 50) private String priorityReasonCode;
    @Column(name = "eligible_since_at") private Instant eligibleSinceAt;
    @Column(name = "called_at") private Instant calledAt;
    @Column(name = "called_by_user_id") private UUID calledByUserId;
    @Column(name = "call_attempts", nullable = false, columnDefinition = "smallint") private int callAttempts;
    @Column(name = "started_at") private Instant startedAt;
    @Column(name = "completed_at") private Instant completedAt;
    @Column(name = "missed_at") private Instant missedAt;
    @Column(name = "cancelled_at") private Instant cancelledAt;
    @Column(name = "missed_count", nullable = false, columnDefinition = "smallint") private int missedCount;
    @Column(name = "estimated_wait_minutes") private Integer estimatedWaitMinutes;
    @Column(name = "near_turn_notified_at") private Instant nearTurnNotifiedAt;
    @Version @Column(nullable = false) private long version;
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @UpdateTimestamp @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @PrePersist void assignId() { if (id == null) id = UUID.randomUUID(); }

    @Transient
    public SchedulingLane getSchedulingLane() {
        if (queueType != QueueType.CONSULTATION) return null;
        if (consultationPhase == ConsultationPhase.RESULT_REVIEW) return SchedulingLane.RESULT_REVIEW;
        return queueClass == QueueClass.PRIORITY ? SchedulingLane.PRIORITY : SchedulingLane.NORMAL;
    }

    @Transient
    public boolean isWaitingForCall() {
        return queueType == QueueType.CONSULTATION && consultationPhase == ConsultationPhase.INITIAL
                ? status == QueueStatus.CHECKED_IN
                : status == QueueStatus.QUEUED || status == QueueStatus.CHECKED_IN;
    }
}
