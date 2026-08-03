package com.careflow.patient.dto.response;

import com.careflow.patient.model.Gender;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PatientResponse {
    private UUID id;
    private UUID userId;
    private String fullName;
    private LocalDate dateOfBirth;
    private Gender gender;
    private String phone;
    private String idCardNumber;
    private String insuranceNumber;
    private String occupation;
    private String address;
    private String avatarUrl;
    private Instant createdAt;
    private Instant updatedAt;
}

