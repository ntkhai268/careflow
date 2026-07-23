package com.careflow.patient.mapper;

import com.careflow.patient.dto.response.PatientResponse;
import com.careflow.patient.model.Patient;

public class PatientMapper {

    private PatientMapper() {}

    public static PatientResponse toResponse(Patient patient) {
        return PatientResponse.builder()
                .id(patient.getId())
                .userId(patient.getUserId())
                .fullName(patient.getFullName())
                .dateOfBirth(patient.getDateOfBirth())
                .gender(patient.getGender())
                .phone(patient.getPhone())
                .idCardNumber(patient.getIdCardNumber())
                .insuranceNumber(patient.getInsuranceNumber())
                .occupation(patient.getOccupation())
                .address(patient.getAddress())
                .avatarUrl(patient.getAvatarUrl())
                .createdAt(patient.getCreatedAt() != null ? java.time.LocalDateTime.ofInstant(patient.getCreatedAt(), java.time.ZoneId.systemDefault()) : null)
                .updatedAt(patient.getUpdatedAt() != null ? java.time.LocalDateTime.ofInstant(patient.getUpdatedAt(), java.time.ZoneId.systemDefault()) : null)
                .build();
    }
}
