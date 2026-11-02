package com.careflow.emr.service;

import com.careflow.common.exception.BusinessException;
import com.careflow.common.exception.ResourceNotFoundException;
import com.careflow.emr.client.ConsultationClient;
import com.careflow.emr.client.LabClient;
import com.careflow.emr.client.PatientClient;
import com.careflow.emr.client.PrescriptionClient;
import com.careflow.emr.dto.request.CreateMedicalRecordRequest;
import com.careflow.emr.dto.request.UpdateMedicalRecordRequest;
import com.careflow.emr.dto.response.MedicalRecordResponse;
import com.careflow.emr.dto.response.PatientSummaryResponse;
import com.careflow.emr.mapper.MedicalRecordMapper;
import com.careflow.emr.model.MedicalRecord;
import com.careflow.emr.repository.MedicalRecordRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Year;
import java.util.UUID;

@Service
@Slf4j
public class MedicalRecordService {

    private final MedicalRecordRepository medicalRecordRepository;
    private final MedicalRecordMapper medicalRecordMapper;
    private final EmrClinicalSummaryAggregator summaryAggregator;

    public MedicalRecordService(MedicalRecordRepository medicalRecordRepository,
                                MedicalRecordMapper medicalRecordMapper,
                                PatientClient patientClient,
                                ConsultationClient consultationClient,
                                PrescriptionClient prescriptionClient,
                                LabClient labClient) {
        this.medicalRecordRepository = medicalRecordRepository;
        this.medicalRecordMapper = medicalRecordMapper;
        this.summaryAggregator = new EmrClinicalSummaryAggregator(
                patientClient,
                consultationClient,
                prescriptionClient,
                labClient);
    }

    @Transactional
    public MedicalRecordResponse createMedicalRecord(CreateMedicalRecordRequest request) {
        if (medicalRecordRepository.existsByPatientId(request.getPatientId())) {
            throw new BusinessException(400, "Bệnh nhân đã có hồ sơ bệnh án EMR");
        }

        String recordNumber = generateRecordNumber();
        MedicalRecord record = MedicalRecord.create(
                request.getPatientId(),
                recordNumber,
                request.getBloodType(),
                request.getMedicalHistory());

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

        record.applyUpdates(request.getBloodType(), request.getMedicalHistory());

        MedicalRecord saved = medicalRecordRepository.save(record);
        log.info("Updated MedicalRecord for patient {}", patientId);
        return medicalRecordMapper.toResponse(saved);
    }

    public PatientSummaryResponse getPatientSummary(UUID patientId) {
        log.info("[AUDIT LOG] Tra cứu Hồ sơ Bệnh án Điện tử EMR 360° cho Bệnh nhân ID: {}", patientId);
        // Read existing master metadata; a GET must never create a clinical record.
        MedicalRecord record = medicalRecordRepository.findByPatientId(patientId).orElse(null);
        return summaryAggregator.aggregate(patientId, record);
    }

    private synchronized String generateRecordNumber() {
        int year = Year.now().getValue();
        long count = medicalRecordRepository.count() + 1;
        return String.format("EMR-%d-%06d", year, count);
    }
}
