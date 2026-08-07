package com.careflow.directory.model;

import com.careflow.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "staff_assignments", indexes = {
        @Index(name = "uk_staff_assignment_user_room", columnList = "userId, roomId", unique = true),
        @Index(name = "idx_staff_assignment_room", columnList = "roomId, isActive")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StaffAssignment extends BaseEntity {

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 50)
    private String roomId;

    @Column(nullable = false, length = 50)
    private String departmentCode;

    @Column(nullable = false)
    @Builder.Default
    private Boolean isActive = true;
}
