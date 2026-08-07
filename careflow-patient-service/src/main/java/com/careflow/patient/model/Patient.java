package com.careflow.patient.model;

import com.careflow.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "patients", indexes = {
        @Index(name = "idx_patient_user_id", columnList = "userId", unique = true),
        @Index(name = "idx_patient_id_card", columnList = "idCardNumber", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Patient extends BaseEntity {

    @Column(nullable = false, unique = true)
    private UUID userId;

    @Column(nullable = false, length = 100)
    private String fullName;

    private LocalDate dateOfBirth;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private Gender gender;

    @Column(length = 15)
    private String phone;

    @Column(length = 20, unique = true)
    private String idCardNumber;

    @Column(length = 20)
    private String insuranceNumber;

    @Column(length = 100)
    private String occupation;

    @Column(length = 500)
    private String address;

    @Column(length = 255)
    private String avatarUrl;

    @Column(length = 500)
    private String allergyNotes;

    @Column(length = 1000)
    private String medicalHistory;

    @OneToMany(mappedBy = "patient", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private java.util.List<PatientAllergy> allergies = new java.util.ArrayList<>();
}
