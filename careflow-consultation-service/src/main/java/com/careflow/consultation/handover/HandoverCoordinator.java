package com.careflow.consultation.handover;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;
@Entity @Table(name="handover_coordinators") @Getter @Setter @NoArgsConstructor
public class HandoverCoordinator {
    @Id private UUID doctorId;
    private boolean active;
    @Column(nullable=false) private UUID grantedBy;
    @Column(nullable=false) private Instant grantedAt;
    @Column(nullable=false,columnDefinition="text") private String grantReason;
    private UUID revokedBy;
    private Instant revokedAt;
    @Column(columnDefinition="text") private String revokeReason;
}
