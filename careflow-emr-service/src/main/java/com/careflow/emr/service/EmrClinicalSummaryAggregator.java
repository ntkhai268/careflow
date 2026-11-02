package com.careflow.emr.service;

import com.careflow.common.dto.ApiResponse;
import com.careflow.common.exception.BusinessException;
import com.careflow.emr.client.ConsultationClient;
import com.careflow.emr.client.LabClient;
import com.careflow.emr.client.PatientClient;
import com.careflow.emr.client.PrescriptionClient;
import com.careflow.emr.dto.response.PatientSummaryResponse;
import com.careflow.emr.model.MedicalRecord;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

@Slf4j
final class EmrClinicalSummaryAggregator {
    private static final String DEFAULT_ALLERGY_SEVERITY = "INFO";

    private final PatientClient patientClient;
    private final ConsultationClient consultationClient;
    private final PrescriptionClient prescriptionClient;
    private final LabClient labClient;

    EmrClinicalSummaryAggregator(PatientClient patientClient,
                                 ConsultationClient consultationClient,
                                 PrescriptionClient prescriptionClient,
                                 LabClient labClient) {
        this.patientClient = patientClient;
        this.consultationClient = consultationClient;
        this.prescriptionClient = prescriptionClient;
        this.labClient = labClient;
    }

    PatientSummaryResponse aggregate(UUID patientId, MedicalRecord record) {
        PatientSummaryResponse.MedicalRecordInfo recordInfo = toMedicalRecordInfo(record);
        PatientSnapshot patientSnapshot = fetchPatientSnapshot(patientId);
        List<Object> consultations = fetchProviderList(
                () -> consultationClient.getConsultationsByPatient(patientId),
                "consultations",
                patientId,
                "Consultation history is unavailable");
        List<Object> prescriptions = fetchProviderList(
                () -> prescriptionClient.getPrescriptionsByPatient(patientId),
                "prescriptions",
                patientId,
                "Prescription history is unavailable");
        List<Object> labOrders = fetchProviderList(
                () -> labClient.getLabOrdersByPatient(patientId),
                "lab orders",
                patientId,
                "Laboratory history is unavailable");

        return PatientSummaryResponse.builder()
                .patient(patientSnapshot.patientInfo())
                .medicalRecord(recordInfo)
                .allergies(patientSnapshot.allergies())
                .recentConsultations(consultations)
                .recentPrescriptions(prescriptions)
                .recentLabOrders(labOrders)
                .build();
    }

    private PatientSummaryResponse.MedicalRecordInfo toMedicalRecordInfo(MedicalRecord record) {
        if (record == null) {
            return PatientSummaryResponse.MedicalRecordInfo.builder().build();
        }
        return PatientSummaryResponse.MedicalRecordInfo.builder()
                .id(record.getId())
                .recordNumber(record.getRecordNumber())
                .bloodType(record.getBloodType())
                .medicalHistory(record.getMedicalHistory())
                .build();
    }

    private PatientSnapshot fetchPatientSnapshot(UUID patientId) {
        try {
            ApiResponse<Map<String, Object>> response = patientClient.getPatientById(patientId);
            if (response == null || response.getData() == null) {
                throw new BusinessException(503, "Patient information is unavailable");
            }
            Map<String, Object> data = response.getData();
            PatientSummaryResponse.PatientInfo patientInfo = PatientSummaryResponse.PatientInfo.builder()
                    .id(parseUuidOrDefault(data.get("id"), patientId))
                    .userId(parseUuidOrDefault(data.get("userId"), null))
                    .fullName((String) data.get("fullName"))
                    .dateOfBirth((String) data.get("dateOfBirth"))
                    .gender(data.get("gender") != null ? data.get("gender").toString() : null)
                    .phone((String) data.get("phone"))
                    .idCardNumber((String) data.get("idCardNumber"))
                    .insuranceNumber((String) data.get("insuranceNumber"))
                    .occupation((String) data.get("occupation"))
                    .address((String) data.get("address"))
                    .avatarUrl((String) data.get("avatarUrl"))
                    .build();
            List<PatientSummaryResponse.AllergyInfo> allergies = parseAllergies(data.get("allergies"));
            return new PatientSnapshot(patientInfo, allergies);
        } catch (Exception ex) {
            log.warn("Failed to fetch patient details for ID {}: {}", patientId, ex.getMessage());
            throw new BusinessException(503, "Patient information is unavailable");
        }
    }

    @SuppressWarnings("unchecked")
    private List<PatientSummaryResponse.AllergyInfo> parseAllergies(Object rawAllergiesObj) {
        List<PatientSummaryResponse.AllergyInfo> allergies = new ArrayList<>();
        if (rawAllergiesObj instanceof List<?> rawList) {
            for (Object item : rawList) {
                if (item instanceof Map<?, ?> rawMap) {
                    Map<String, Object> allergy = (Map<String, Object>) rawMap;
                    allergies.add(PatientSummaryResponse.AllergyInfo.builder()
                            .id(parseUuidOrDefault(allergy.get("id"), null))
                            .allergyName((String) allergy.get("allergyName"))
                            .allergyGroup((String) allergy.get("allergyGroup"))
                            .severity(allergy.get("severity") != null
                                    ? allergy.get("severity").toString()
                                    : DEFAULT_ALLERGY_SEVERITY)
                            .reaction((String) allergy.get("reaction"))
                            .confirmedBy((String) allergy.get("confirmedBy"))
                            .build());
                }
            }
        }
        return allergies;
    }

    private List<Object> fetchProviderList(Supplier<ApiResponse<List<Map<String, Object>>>> call,
                                           String domainLabel,
                                           UUID patientId,
                                           String unavailableMessage) {
        try {
            ApiResponse<List<Map<String, Object>>> response = call.get();
            if (response == null || response.getData() == null) {
                throw new BusinessException(503, unavailableMessage);
            }
            return new ArrayList<>(response.getData());
        } catch (Exception ex) {
            log.warn("Failed to fetch {} for patient ID {}: {}", domainLabel, patientId, ex.getMessage());
            throw new BusinessException(503, unavailableMessage);
        }
    }

    private static UUID parseUuidOrDefault(Object rawValue, UUID fallback) {
        return rawValue != null ? UUID.fromString(rawValue.toString()) : fallback;
    }

    private record PatientSnapshot(
            PatientSummaryResponse.PatientInfo patientInfo,
            List<PatientSummaryResponse.AllergyInfo> allergies
    ) {}
}
