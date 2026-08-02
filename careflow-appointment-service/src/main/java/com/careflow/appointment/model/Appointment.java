package com.careflow.appointment.model;

import com.careflow.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "appointments", indexes = {
        @Index(name = "idx_appointment_patient", columnList = "patientId"),
        @Index(name = "idx_appointment_dept_date", columnList = "department, appointmentDate"),
        @Index(name = "idx_appointment_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Appointment extends BaseEntity {

    @Column(nullable = false)
    private UUID patientId;

    @Column(nullable = false)
    private UUID ownerUserId;

    @Column(length = 100)
    private String patientName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Department department;

    @Column(nullable = false)
    private UUID departmentId;

    @Column(nullable = false, length = 50)
    private String roomId;

    @Column(nullable = false, length = 150)
    private String roomDisplayName;

    private UUID doctorId;

    @Column(length = 100)
    private String doctorName;

    @Column(nullable = false)
    private LocalDate appointmentDate;

    @Column(nullable = false, length = 20)
    private String timeSlot;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private AppointmentStatus status = AppointmentStatus.PENDING;

    @Column(length = 500)
    private String reason;

    @Column(length = 1000)
    private String notes;

    @Column(length = 20)
    private String queueNumber;
}
