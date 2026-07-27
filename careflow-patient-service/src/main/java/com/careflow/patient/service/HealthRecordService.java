package com.careflow.patient.service;

import com.careflow.common.exception.ResourceNotFoundException;
import com.careflow.patient.dto.request.CreateHealthRecordRequest;
import com.careflow.patient.dto.response.HealthRecordResponse;
import com.careflow.patient.model.HealthRecord;
import com.careflow.patient.model.HealthRecordFile;
import com.careflow.patient.repository.HealthRecordRepository;
import com.careflow.patient.repository.PatientRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class HealthRecordService {

    private final HealthRecordRepository healthRecordRepository;
    private final PatientRepository patientRepository;
    private final FileStorageService fileStorageService;

    @Transactional
    public HealthRecordResponse create(UUID patientId, CreateHealthRecordRequest request, List<MultipartFile> files) {
        if (!patientRepository.existsById(patientId)) {
            throw new ResourceNotFoundException("Patient", "id", patientId);
        }

        BigDecimal bmi = null;
        if (request.getHeightCm() != null && request.getWeightKg() != null && request.getHeightCm().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal heightInMeters = request.getHeightCm().divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
            bmi = request.getWeightKg().divide(heightInMeters.multiply(heightInMeters), 2, RoundingMode.HALF_UP);
        }

        HealthRecord record = HealthRecord.builder()
                .patientId(patientId)
                .title(request.getTitle())
                .recordDate(request.getRecordDate())
                .facilityName(request.getFacilityName())
                .notes(request.getNotes())
                .bloodSugar(request.getBloodSugar())
                .bloodPressure(request.getBloodPressure())
                .heightCm(request.getHeightCm())
                .weightKg(request.getWeightKg())
                .bmi(bmi)
                .waistCm(request.getWaistCm())
                .bloodType(request.getBloodType())
                .pulse(request.getPulse())
                .temperature(request.getTemperature())
                .respiratoryRate(request.getRespiratoryRate())
                .drugAllergy(request.getDrugAllergy())
                .chemicalAllergy(request.getChemicalAllergy())
                .foodAllergy(request.getFoodAllergy())
                .heartDisease(request.getHeartDisease())
                .hypertension(request.getHypertension())
                .mentalIllness(request.getMentalIllness())
                .cancer(request.getCancer())
                .asthma(request.getAsthma())
                .epilepsy(request.getEpilepsy())
                .tuberculosis(request.getTuberculosis())
                .build();

        if (files != null && !files.isEmpty()) {
            for (MultipartFile file : files) {
                if (file != null && !file.isEmpty()) {
                    String storedPath = fileStorageService.store(file);
                    HealthRecordFile recordFile = HealthRecordFile.builder()
                            .fileName(file.getOriginalFilename())
                            .storedPath(storedPath)
                            .fileSize(file.getSize())
                            .contentType(file.getContentType())
                            .build();
                    record.addFile(recordFile);
                }
            }
        }

        HealthRecord savedRecord = healthRecordRepository.save(record);
        return HealthRecordResponse.from(savedRecord, getBaseUrl());
    }

    @Transactional(readOnly = true)
    public List<HealthRecordResponse> getAll(UUID patientId) {
        if (!patientRepository.existsById(patientId)) {
            throw new ResourceNotFoundException("Patient", "id", patientId);
        }
        return healthRecordRepository.findByPatientIdOrderByRecordDateDesc(patientId)
                .stream()
                .map(record -> HealthRecordResponse.from(record, getBaseUrl()))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public HealthRecordResponse getById(UUID patientId, UUID recordId) {
        HealthRecord record = healthRecordRepository.findByIdAndPatientId(recordId, patientId)
                .orElseThrow(() -> new ResourceNotFoundException("HealthRecord", "id", recordId));
        return HealthRecordResponse.from(record, getBaseUrl());
    }

    @Transactional
    public HealthRecordResponse update(UUID patientId, UUID recordId, com.careflow.patient.dto.request.UpdateHealthRecordRequest request) {
        HealthRecord record = healthRecordRepository.findByIdAndPatientId(recordId, patientId)
                .orElseThrow(() -> new ResourceNotFoundException("HealthRecord", "id", recordId));

        if (request.getTitle() != null) record.setTitle(request.getTitle());
        if (request.getRecordDate() != null) record.setRecordDate(request.getRecordDate());
        if (request.getFacilityName() != null) record.setFacilityName(request.getFacilityName());
        record.setNotes(request.getNotes());
        record.setBloodSugar(request.getBloodSugar());
        record.setBloodPressure(request.getBloodPressure());
        record.setHeightCm(request.getHeightCm());
        record.setWeightKg(request.getWeightKg());
        record.setWaistCm(request.getWaistCm());
        record.setBloodType(request.getBloodType());
        record.setPulse(request.getPulse());
        record.setTemperature(request.getTemperature());
        record.setRespiratoryRate(request.getRespiratoryRate());
        record.setDrugAllergy(request.getDrugAllergy());
        record.setChemicalAllergy(request.getChemicalAllergy());
        record.setFoodAllergy(request.getFoodAllergy());
        record.setHeartDisease(request.getHeartDisease());
        record.setHypertension(request.getHypertension());
        record.setMentalIllness(request.getMentalIllness());
        record.setCancer(request.getCancer());
        record.setAsthma(request.getAsthma());
        record.setEpilepsy(request.getEpilepsy());
        record.setTuberculosis(request.getTuberculosis());

        // Recalculate BMI
        if (record.getHeightCm() != null && record.getWeightKg() != null && record.getHeightCm().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal heightInMeters = record.getHeightCm().divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
            record.setBmi(record.getWeightKg().divide(heightInMeters.multiply(heightInMeters), 2, RoundingMode.HALF_UP));
        } else {
            record.setBmi(null);
        }

        HealthRecord saved = healthRecordRepository.save(record);
        return HealthRecordResponse.from(saved, getBaseUrl());
    }

    @Transactional
    public void delete(UUID patientId, UUID recordId) {
        HealthRecord record = healthRecordRepository.findByIdAndPatientId(recordId, patientId)
                .orElseThrow(() -> new ResourceNotFoundException("HealthRecord", "id", recordId));
        
        if (record.getFiles() != null) {
            for (HealthRecordFile file : record.getFiles()) {
                fileStorageService.delete(file.getStoredPath());
            }
        }
        
        healthRecordRepository.delete(record);
    }

    private String getBaseUrl() {
        try {
            return ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
        } catch (Exception e) {
            return ""; // fallback for tests or when context is not available
        }
    }
}
