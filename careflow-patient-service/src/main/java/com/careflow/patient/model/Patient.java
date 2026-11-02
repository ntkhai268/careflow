package com.careflow.patient.model;

import com.careflow.common.model.BaseEntity;
import com.careflow.patient.dto.request.UpdatePatientRequest;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "patients", indexes = {
        @Index(name = "idx_patient_user_id", columnList = "userId"),
        @Index(name = "idx_patient_id_card", columnList = "idCardNumber"),
        @Index(name = "uk_patient_user_id_card_number", columnList = "userId, idCardNumber", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Patient extends BaseEntity {

    @Column
    private UUID userId;

    @Column(name = "reception_created_by_user_id")
    private UUID receptionCreatedByUserId;

    @Column(name = "reception_room_id", length = 50)
    private String receptionRoomId;

    @Column(nullable = false, length = 100)
    private String fullName;

    private LocalDate dateOfBirth;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private Gender gender;

    @Column(length = 15)
    private String phone;

    @Column(length = 20)
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
    private List<PatientAllergy> allergies = new ArrayList<>();

    public void applyUpdates(UpdatePatientRequest request) {
        if (request.getFullName() != null) {
            this.fullName = request.getFullName();
        }
        if (request.getDateOfBirth() != null) {
            this.dateOfBirth = request.getDateOfBirth();
        }
        if (request.getGender() != null) {
            this.gender = request.getGender();
        }
        if (request.getPhone() != null) {
            this.phone = request.getPhone();
        }
        if (request.getIdCardNumber() != null) {
            this.idCardNumber = request.getIdCardNumber();
        }
        if (request.getInsuranceNumber() != null) {
            this.insuranceNumber = request.getInsuranceNumber();
        }
        if (request.getOccupation() != null) {
            this.occupation = request.getOccupation();
        }
        if (request.getAddress() != null) {
            this.address = request.getAddress();
        }
        if (request.getAvatarUrl() != null) {
            this.avatarUrl = request.getAvatarUrl();
        }
        if (request.getAllergyNotes() != null) {
            this.allergyNotes = request.getAllergyNotes();
        }
        if (request.getMedicalHistory() != null) {
            this.medicalHistory = request.getMedicalHistory();
        }
    }
}
