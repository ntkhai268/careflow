package com.careflow.emr.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PatientSummaryResponse {

    private PatientInfo patient;
    private MedicalRecordInfo medicalRecord;
    private List<AllergyInfo> allergies;
    private List<Object> recentConsultations;
    private List<Object> recentPrescriptions;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PatientInfo {
        private UUID id;
        private UUID userId;
        private String fullName;
        private String dateOfBirth;
        private String gender;
        private String phone;
        private String idCardNumber;
        private String insuranceNumber;
        private String occupation;
        private String address;
        private String avatarUrl;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MedicalRecordInfo {
        private UUID id;
        private String recordNumber;
        private String bloodType;
        private String medicalHistory;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AllergyInfo {
        private UUID id;
        private String allergyName;
        private String allergyGroup;
        private String severity;
        private String reaction;
        private String confirmedBy;
    }
}
