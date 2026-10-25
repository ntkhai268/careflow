package com.careflow.consultation.handover;
import com.careflow.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;
@Entity @Table(name="handover_coordinator_audit") @Getter @Setter @NoArgsConstructor
public class CoordinatorAudit extends BaseEntity {
    @Column(nullable=false) private UUID doctorId;
    @Column(nullable=false) private UUID actorId;
    @Column(nullable=false) private boolean granted;
    @Column(nullable=false,columnDefinition="text") private String reason;
}
