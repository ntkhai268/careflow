package com.careflow.patient.controller;

import com.careflow.common.dto.ApiResponse;
import com.careflow.patient.dto.request.CreateHealthRecordRequest;
import com.careflow.patient.dto.request.UpdateHealthRecordRequest;
import com.careflow.patient.dto.response.HealthRecordResponse;
import com.careflow.patient.service.FileStorageService;
import com.careflow.patient.service.HealthRecordService;
import com.careflow.patient.model.HealthRecordFile;
import com.careflow.patient.repository.HealthRecordFileRepository;
import com.careflow.patient.repository.HealthRecordRepository;
import com.careflow.common.exception.BusinessException;
import com.careflow.common.exception.ResourceNotFoundException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Tag(name = "Health Records", description = "Patient health records API")
public class HealthRecordController {

    private final HealthRecordService healthRecordService;
    private final FileStorageService fileStorageService;
    private final HealthRecordFileRepository fileRepository;
    private final HealthRecordRepository healthRecordRepository;

    @PostMapping(value = "/api/patients/{patientId}/health-records", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Create a health record")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<HealthRecordResponse> create(
            @PathVariable UUID patientId,
            @RequestPart("request") @Valid CreateHealthRecordRequest request,
            @RequestPart(value = "files", required = false) List<MultipartFile> files) {
        String baseUrl = ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
        HealthRecordResponse response = healthRecordService.create(patientId, request, files, baseUrl);
        return ApiResponse.success(response);
    }

    @GetMapping("/api/patients/{patientId}/health-records")
    @Operation(summary = "Get all health records for a patient")
    public ApiResponse<List<HealthRecordResponse>> getAll(@PathVariable UUID patientId) {
        String baseUrl = ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
        List<HealthRecordResponse> response = healthRecordService.getAll(patientId, baseUrl);
        return ApiResponse.success(response);
    }

    @GetMapping("/api/patients/{patientId}/health-records/{id}")
    @Operation(summary = "Get a specific health record")
    public ApiResponse<HealthRecordResponse> getById(@PathVariable UUID patientId, @PathVariable UUID id) {
        String baseUrl = ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
        HealthRecordResponse response = healthRecordService.getById(patientId, id, baseUrl);
        return ApiResponse.success(response);
    }

    @PutMapping("/api/patients/{patientId}/health-records/{id}")
    @Operation(summary = "Update a health record")
    public ApiResponse<HealthRecordResponse> update(
            @PathVariable UUID patientId,
            @PathVariable UUID id,
            @RequestBody @Valid UpdateHealthRecordRequest request) {
        String baseUrl = ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
        HealthRecordResponse response = healthRecordService.update(patientId, id, request, baseUrl);
        return ApiResponse.success("Cập nhật hồ sơ thành công", response);
    }

    @DeleteMapping("/api/patients/{patientId}/health-records/{id}")
    @Operation(summary = "Delete a health record")
    public ApiResponse<Void> delete(@PathVariable UUID patientId, @PathVariable UUID id) {
        healthRecordService.delete(patientId, id);
        return ApiResponse.success(null);
    }

    @GetMapping("/api/patients/{patientId}/health-records/files/{fileId}")
    @Operation(summary = "Download a health record file (with ownership check)")
    public ResponseEntity<Resource> downloadFile(
            @PathVariable UUID patientId,
            @PathVariable UUID fileId) {
        // Ownership check: verify file belongs to a record owned by this patient
        HealthRecordFile fileRecord = fileRepository.findById(fileId)
                .orElseThrow(() -> new ResourceNotFoundException("HealthRecordFile", "id", fileId));

        boolean belongs = healthRecordRepository
                .findByIdAndPatientId(fileRecord.getHealthRecord().getId(), patientId)
                .isPresent();
        if (!belongs) {
            throw new BusinessException(403, "File does not belong to this patient");
        }

        Resource resource = fileStorageService.load(fileRecord.getStoredPath());

        String contentType = fileRecord.getContentType();
        if (contentType == null) {
            contentType = "application/octet-stream";
        }

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileRecord.getFileName() + "\"")
                .body(resource);
    }
}
