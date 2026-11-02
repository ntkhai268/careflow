package com.careflow.directory.model;

import com.careflow.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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

    private Boolean primaryForBooking;

    public void applyUpdates(String updatedFullName,
                             String updatedTitle,
                             String updatedDepartmentCode,
                             String updatedAssignedRoomId,
                             String updatedSpecialization,
                             Boolean updatedPrimaryForBooking,
                             String updatedLicenseNumber,
                             Boolean updatedIsActive) {
        if (updatedFullName != null && !updatedFullName.isBlank()) {
            this.fullName = updatedFullName;
        }
        if (updatedTitle != null) {
            this.title = updatedTitle;
        }
        if (updatedDepartmentCode != null) {
            this.departmentCode = updatedDepartmentCode;
        }
        if (updatedAssignedRoomId != null) {
            this.assignedRoomId = updatedAssignedRoomId;
        }
        if (updatedSpecialization != null) {
            this.specialization = updatedSpecialization;
        }
        if (updatedPrimaryForBooking != null) {
            this.primaryForBooking = updatedPrimaryForBooking;
        }
        if (updatedLicenseNumber != null) {
            this.licenseNumber = updatedLicenseNumber;
        }
        if (updatedIsActive != null) {
            this.isActive = updatedIsActive;
        }
    }
}
