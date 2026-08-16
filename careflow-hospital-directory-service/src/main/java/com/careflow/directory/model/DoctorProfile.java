package com.careflow.directory.model;

import com.careflow.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "doctor_profiles", indexes = {
        @Index(name = "uk_doctor_user_id", columnList = "userId", unique = true),
        @Index(name = "idx_doctor_department", columnList = "departmentCode")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DoctorProfile extends BaseEntity {

    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(nullable = false, unique = true)
    private UUID userId;

    @Column(nullable = false, length = 150)
    private String fullName;

    @Column(length = 50)
    private String title;

    @Column(length = 50)
    private String departmentCode;

    @Column(length = 50)
    private String assignedRoomId;

    @Column(length = 100)
    private String specialization;

    @Column(length = 50)
    private String licenseNumber;

    @Builder.Default
    @Column(nullable = false)
    private Boolean isActive = true;
}
