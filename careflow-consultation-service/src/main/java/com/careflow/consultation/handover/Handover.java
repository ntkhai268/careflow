package com.careflow.consultation.handover;

import com.careflow.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "consultation_handovers")
@Getter @Setter @NoArgsConstructor
public class Handover extends BaseEntity {
    public enum Status { PENDING, ACCEPTED, REJECTED, CANCELLED }
    @Column(nullable=false) private UUID consultationId;
    @Column(nullable=false) private UUID patientId;
    @Column(length=100) private String patientName;
    @Column(nullable=false) private UUID fromDoctorId;
    @Column(nullable=false) private UUID toDoctorId;
    @Column(nullable=false) private UUID initiatedBy;
    private String fromDoctorName;
    private String toDoctorName;
    @Column(nullable=false) private String departmentCode;
    @Column(nullable=false) private String roomId;
    @Column(nullable=false, columnDefinition="text") private String reason;
    @Column(nullable=false, columnDefinition="text") private String situation;
    @Column(nullable=false, columnDefinition="text") private String background;
    @Column(nullable=false, columnDefinition="text") private String assessment;
    @Column(nullable=false, columnDefinition="text") private String recommendation;
    @Column(nullable=false) private boolean exceptional;
    @Enumerated(EnumType.STRING) @Column(nullable=false) private Status status = Status.PENDING;
    @Version private Long version = 0L;
    @com.fasterxml.jackson.annotation.JsonIgnore
    @Column(columnDefinition="text") private String clinicalSnapshot;
    private UUID decidedBy;
    private Instant decidedAt;
    @Column(columnDefinition="text") private String decisionNote;
}
