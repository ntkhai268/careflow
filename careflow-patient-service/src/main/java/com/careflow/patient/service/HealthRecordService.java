package com.careflow.patient.service;

import com.careflow.common.exception.BusinessException;
import com.careflow.common.exception.ResourceNotFoundException;
import com.careflow.patient.dto.request.CreateHealthRecordRequest;
import com.careflow.patient.dto.request.UpdateHealthRecordRequest;
import com.careflow.patient.dto.response.HealthRecordResponse;
import com.careflow.patient.model.HealthRecord;
import com.careflow.patient.model.HealthRecordFile;
import com.careflow.patient.repository.HealthRecordRepository;
import com.careflow.patient.repository.PatientRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class HealthRecordService {
    private static final int MAX_FILES_PER_RECORD = 5;
    private static final long MAX_BYTES_PER_RECORD = 50L * 1024 * 1024;

    private final HealthRecordRepository healthRecordRepository;
    private final PatientRepository patientRepository;
    private final FileStorageService fileStorageService;
    private final UploadQuotaService uploadQuotaService;

    @Transactional
    public HealthRecordResponse create(UUID patientId,
                                       CreateHealthRecordRequest request,
                                       List<MultipartFile> files,
                                       String baseUrl) {
        if (!patientRepository.existsById(patientId)) {
            throw new ResourceNotFoundException("Patient", "id", patientId);
        }

        HealthRecord record = HealthRecord.fromCreateRequest(patientId, request);
        attachUploadedFilesIfPresent(patientId, record, files);

        HealthRecord savedRecord = healthRecordRepository.save(record);
        return HealthRecordResponse.from(savedRecord, baseUrl);
    }

    @Transactional(readOnly = true)
    public List<HealthRecordResponse> getAll(UUID patientId, String baseUrl) {
        if (!patientRepository.existsById(patientId)) {
            throw new ResourceNotFoundException("Patient", "id", patientId);
        }
        return healthRecordRepository.findByPatientIdOrderByRecordDateDesc(patientId)
                .stream()
                .map(record -> HealthRecordResponse.from(record, baseUrl))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public HealthRecordResponse getById(UUID patientId, UUID recordId, String baseUrl) {
        HealthRecord record = healthRecordRepository.findByIdAndPatientId(recordId, patientId)
                .orElseThrow(() -> new ResourceNotFoundException("HealthRecord", "id", recordId));
        return HealthRecordResponse.from(record, baseUrl);
    }

    @Transactional
    public HealthRecordResponse update(UUID patientId,
                                       UUID recordId,
                                       UpdateHealthRecordRequest request,
                                       String baseUrl) {
        HealthRecord record = healthRecordRepository.findByIdAndPatientId(recordId, patientId)
                .orElseThrow(() -> new ResourceNotFoundException("HealthRecord", "id", recordId));

        record.applyUpdates(request);

        HealthRecord saved = healthRecordRepository.save(record);
        return HealthRecordResponse.from(saved, baseUrl);
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

    private void attachUploadedFilesIfPresent(UUID patientId, HealthRecord record, List<MultipartFile> files) {
        if (files == null) {
            return;
        }
        long totalBytes = files.stream()
                .filter(Objects::nonNull)
                .mapToLong(MultipartFile::getSize)
                .sum();
        if (files.size() > MAX_FILES_PER_RECORD || totalBytes > MAX_BYTES_PER_RECORD) {
            throw new BusinessException(413, "Mỗi hồ sơ tối đa 5 tệp, tổng dung lượng tối đa 50MB.");
        }
        if (files.isEmpty()) {
            return;
        }

        long nonEmptyFileCount = files.stream()
                .filter(Objects::nonNull)
                .filter(file -> !file.isEmpty())
                .count();
        uploadQuotaService.requireCapacity(patientId, totalBytes, nonEmptyFileCount);

        List<String> storedFiles = new ArrayList<>();
        registerRollbackFileCleanup(storedFiles);

        for (MultipartFile file : files) {
            if (file != null && !file.isEmpty()) {
                String originalFilename = file.getOriginalFilename();
                if (!StringUtils.hasText(originalFilename)) {
                    throw new BusinessException(400, "File must have a filename");
                }
                String storedPath = fileStorageService.store(file);
                storedFiles.add(storedPath);
                HealthRecordFile recordFile = HealthRecordFile.builder()
                        .fileName(originalFilename)
                        .storedPath(storedPath)
                        .fileSize(file.getSize())
                        .contentType(file.getContentType())
                        .build();
                record.addFile(recordFile);
            }
        }
    }

    private void registerRollbackFileCleanup(List<String> storedFiles) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_COMMITTED) {
                    return;
                }
                for (String stored : storedFiles) {
                    try {
                        fileStorageService.delete(stored);
                    } catch (RuntimeException failure) {
                        log.warn("event=UPLOAD_ROLLBACK_CLEANUP_FAILED");
                    }
                }
            }
        });
    }
}
