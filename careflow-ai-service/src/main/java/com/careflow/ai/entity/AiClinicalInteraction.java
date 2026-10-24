package com.careflow.ai.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ai_clinical_interactions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiClinicalInteraction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "consultation_id", nullable = false, columnDefinition = "uuid")
    private UUID consultationId;

    @Column(name = "doctor_id", nullable = false, columnDefinition = "uuid")
    private UUID doctorId;

    @Column(name = "patient_id", columnDefinition = "uuid")
    private UUID patientId;

    @Column(name = "service_mode", nullable = false, length = 50)
    private String serviceMode;

    @Column(name = "user_question", nullable = false, columnDefinition = "text")
    private String userQuestion;

    @Column(name = "prompt_snapshot", columnDefinition = "jsonb")
    private String promptSnapshot;

    @Column(name = "ai_response_summary", columnDefinition = "text")
    private String aiResponseSummary;

    @Column(name = "suggestions", columnDefinition = "jsonb")
    private String suggestions;

    @Column(name = "evidence_refs", columnDefinition = "jsonb")
    private String evidenceRefs;

    @Column(name = "feedback_status", length = 50)
    private String feedbackStatus;

    @Column(name = "doctor_comment", columnDefinition = "text")
    private String doctorComment;

    @Column(name = "latency_ms")
    private Integer latencyMs;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;
}
