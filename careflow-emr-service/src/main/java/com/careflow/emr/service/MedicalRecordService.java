package com.careflow.emr.service;

import com.careflow.common.dto.ApiResponse;
import com.careflow.common.exception.BusinessException;
import com.careflow.common.exception.ResourceNotFoundException;
import com.careflow.emr.client.ConsultationClient;
import com.careflow.emr.client.PatientClient;
import com.careflow.emr.client.PrescriptionClient;
import com.careflow.emr.dto.request.CreateMedicalRecordRequest;
import com.careflow.emr.dto.request.UpdateMedicalRecordRequest;
import com.careflow.emr.dto.response.MedicalRecordResponse;
import com.careflow.emr.dto.response.PatientSummaryResponse;
import com.careflow.emr.mapper.MedicalRecordMapper;
import com.careflow.emr.model.MedicalRecord;
import com.careflow.emr.repository.MedicalRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Year;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class MedicalRecordService {

    private final MedicalRecordRepository medicalRecordRepository;
    private final MedicalRecordMapper medicalRecordMapper;
    private final PatientClient patientClient;
    private final ConsultationClient consultationClient;
    private final PrescriptionClient prescriptionClient;

    @Transactional
    public MedicalRecordResponse createMedicalRecord(CreateMedicalRecordRequest request) {
        if (medicalRecordRepository.existsByPatientId(request.getPatientId())) {
            throw new BusinessException(400, "Bệnh nhân đã có hồ sơ bệnh án EMR");
        }

        String recordNumber = generateRecordNumber();

        MedicalRecord record = MedicalRecord.builder()
                .patientId(request.getPatientId())
                .recordNumber(recordNumber)
                .bloodType(request.getBloodType() != null ? request.getBloodType() : "Chưa rõ")
                .medicalHistory(request.getMedicalHistory())
                .build();

        MedicalRecord saved = medicalRecordRepository.save(record);
        log.info("Created MedicalRecord {} for patient {}", saved.getRecordNumber(), saved.getPatientId());
        return medicalRecordMapper.toResponse(saved);
    }

    public MedicalRecordResponse getMedicalRecordByPatientId(UUID patientId) {
        MedicalRecord record = medicalRecordRepository.findByPatientId(patientId)
                .orElseThrow(() -> new ResourceNotFoundException("MedicalRecord", "patientId", patientId));
        return medicalRecordMapper.toResponse(record);
    }

    @Transactional
    public MedicalRecordResponse updateMedicalRecord(UUID patientId, UpdateMedicalRecordRequest request) {
        MedicalRecord record = medicalRecordRepository.findByPatientId(patientId)
                .orElseThrow(() -> new ResourceNotFoundException("MedicalRecord", "patientId", patientId));

        if (request.getBloodType() != null) {
            record.setBloodType(request.getBloodType());
        }
        if (request.getMedicalHistory() != null) {
            record.setMedicalHistory(request.getMedicalHistory());
        }

        MedicalRecord saved = medicalRecordRepository.save(record);
        log.info("Updated MedicalRecord for patient {}", patientId);
        return medicalRecordMapper.toResponse(saved);
    }

    @SuppressWarnings("unchecked")
    public PatientSummaryResponse getPatientSummary(UUID patientId) {
        // 1. Lấy thông tin Master Medical Record từ EMR DB (nếu chưa có thì tự tạo mới)
        MedicalRecord record = medicalRecordRepository.findByPatientId(patientId)
                .orElseGet(() -> {
                    MedicalRecord newRecord = MedicalRecord.builder()
                            .patientId(patientId)
                            .recordNumber(generateRecordNumber())
                            .bloodType("Chưa rõ")
                            .medicalHistory("Chưa ghi nhận")
                            .build();
                    return medicalRecordRepository.save(newRecord);
                });

        PatientSummaryResponse.MedicalRecordInfo recordInfo = PatientSummaryResponse.MedicalRecordInfo.builder()
                .id(record.getId())
                .recordNumber(record.getRecordNumber())
                .bloodType(record.getBloodType())
                .medicalHistory(record.getMedicalHistory())
                .build();

        // 2. Gọi Feign Client tới Patient Service
        PatientSummaryResponse.PatientInfo patientInfo = null;
        List<PatientSummaryResponse.AllergyInfo> allergyList = new ArrayList<>();
        try {
            ApiResponse<Map<String, Object>> patientRes = patientClient.getPatientById(patientId);
            if (patientRes != null && patientRes.getData() != null) {
                Map<String, Object> pData = patientRes.getData();
                patientInfo = PatientSummaryResponse.PatientInfo.builder()
                        .id(pData.get("id") != null ? UUID.fromString(pData.get("id").toString()) : patientId)
                        .userId(pData.get("userId") != null ? UUID.fromString(pData.get("userId").toString()) : null)
                        .fullName((String) pData.get("fullName"))
                        .dateOfBirth((String) pData.get("dateOfBirth"))
                        .gender(pData.get("gender") != null ? pData.get("gender").toString() : null)
                        .phone((String) pData.get("phone"))
                        .idCardNumber((String) pData.get("idCardNumber"))
                        .insuranceNumber((String) pData.get("insuranceNumber"))
                        .occupation((String) pData.get("occupation"))
                        .address((String) pData.get("address"))
                        .avatarUrl((String) pData.get("avatarUrl"))
                        .build();

                // Parse allergies
                if (pData.get("allergies") instanceof List) {
                    List<Map<String, Object>> rawAllergies = (List<Map<String, Object>>) pData.get("allergies");
                    for (Map<String, Object> a : rawAllergies) {
                        allergyList.add(PatientSummaryResponse.AllergyInfo.builder()
                                .id(a.get("id") != null ? UUID.fromString(a.get("id").toString()) : null)
                                .allergyName((String) a.get("allergyName"))
                                .allergyGroup((String) a.get("allergyGroup"))
                                .severity(a.get("severity") != null ? a.get("severity").toString() : "INFO")
                                .reaction((String) a.get("reaction"))
                                .confirmedBy((String) a.get("confirmedBy"))
                                .build());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to fetch patient details for ID {}: {}", patientId, e.getMessage());
        }

        // 3. Gọi Feign Client tới Consultation Service
        List<Object> consultations = new ArrayList<>();
        try {
            ApiResponse<List<Map<String, Object>>> cRes = consultationClient.getConsultationsByPatient(patientId);
            if (cRes != null && cRes.getData() != null) {
                consultations.addAll(cRes.getData());
            }
        } catch (Exception e) {
            log.warn("Failed to fetch consultations for patient ID {}: {}", patientId, e.getMessage());
        }

        // 4. Gọi Feign Client tới Prescription Service
        List<Object> prescriptions = new ArrayList<>();
        try {
            ApiResponse<List<Map<String, Object>>> pRes = prescriptionClient.getPrescriptionsByPatient(patientId);
            if (pRes != null && pRes.getData() != null) {
                prescriptions.addAll(pRes.getData());
            }
        } catch (Exception e) {
            log.warn("Failed to fetch prescriptions for patient ID {}: {}", patientId, e.getMessage());
        }

        return PatientSummaryResponse.builder()
                .patient(patientInfo)
                .medicalRecord(recordInfo)
                .allergies(allergyList)
                .recentConsultations(consultations)
                .recentPrescriptions(prescriptions)
                .build();
    }

    private synchronized String generateRecordNumber() {
        int year = Year.now().getValue();
        long count = medicalRecordRepository.count() + 1;
        return String.format("EMR-%d-%06d", year, count);
    }
}
