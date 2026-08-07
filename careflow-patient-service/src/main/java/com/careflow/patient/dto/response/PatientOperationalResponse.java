package com.careflow.patient.dto.response;

import com.careflow.patient.model.Gender;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PatientOperationalResponse {
    private UUID id;
    private String fullName;
    private LocalDate dateOfBirth;
    private Gender gender;
}
