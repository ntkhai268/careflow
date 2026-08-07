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
                .allergyNotes(patient.getAllergyNotes())
                .medicalHistory(patient.getMedicalHistory())
                .allergies(patient.getAllergies() != null ? patient.getAllergies().stream().map(PatientMapper::toAllergyResponse).collect(java.util.stream.Collectors.toList()) : java.util.Collections.emptyList())
                .createdAt(patient.getCreatedAt())
                .updatedAt(patient.getUpdatedAt())
                .build();
    }

    public static com.careflow.patient.dto.response.PatientAllergyResponse toAllergyResponse(com.careflow.patient.model.PatientAllergy allergy) {
        if (allergy == null) return null;
        return com.careflow.patient.dto.response.PatientAllergyResponse.builder()
                .id(allergy.getId())
                .patientId(allergy.getPatient() != null ? allergy.getPatient().getId() : null)
                .allergyName(allergy.getAllergyName())
                .allergyGroup(allergy.getAllergyGroup())
                .severity(allergy.getSeverity())
                .reaction(allergy.getReaction())
                .confirmedBy(allergy.getConfirmedBy())
                .createdAt(allergy.getCreatedAt())
                .build();
    }
}
