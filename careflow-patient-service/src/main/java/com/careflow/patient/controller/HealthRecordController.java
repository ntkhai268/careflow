package com.careflow.patient.controller;

import com.careflow.common.dto.ApiResponse;
import com.careflow.patient.dto.request.CreateHealthRecordRequest;
import com.careflow.patient.dto.response.HealthRecordResponse;
import com.careflow.patient.service.FileStorageService;
import com.careflow.patient.service.HealthRecordService;
import com.careflow.patient.model.HealthRecordFile;
import com.careflow.patient.repository.HealthRecordFileRepository;
import com.careflow.common.exception.ResourceNotFoundException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Tag(name = "Health Records", description = "Patient health records API")
public class HealthRecordController {

    private final HealthRecordService healthRecordService;
    private final FileStorageService fileStorageService;
    private final HealthRecordFileRepository fileRepository;

    @PostMapping(value = "/api/patients/{patientId}/health-records", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Create a health record")
    public ApiResponse<HealthRecordResponse> create(
            @PathVariable UUID patientId,
            @RequestPart("request") @Valid CreateHealthRecordRequest request,
            @RequestPart(value = "files", required = false) List<MultipartFile> files) {
        HealthRecordResponse response = healthRecordService.create(patientId, request, files);
        return ApiResponse.success(response);
    }

    @GetMapping("/api/patients/{patientId}/health-records")
    @Operation(summary = "Get all health records for a patient")
    public ApiResponse<List<HealthRecordResponse>> getAll(@PathVariable UUID patientId) {
        List<HealthRecordResponse> response = healthRecordService.getAll(patientId);
        return ApiResponse.success(response);
    }

    @GetMapping("/api/patients/{patientId}/health-records/{id}")
    @Operation(summary = "Get a specific health record")
    public ApiResponse<HealthRecordResponse> getById(@PathVariable UUID patientId, @PathVariable UUID id) {
        HealthRecordResponse response = healthRecordService.getById(patientId, id);
        return ApiResponse.success(response);
    }

    @PutMapping("/api/patients/{patientId}/health-records/{id}")
    @Operation(summary = "Update a health record")
    public ApiResponse<HealthRecordResponse> update(
            @PathVariable UUID patientId,
            @PathVariable UUID id,
            @RequestBody com.careflow.patient.dto.request.UpdateHealthRecordRequest request) {
        HealthRecordResponse response = healthRecordService.update(patientId, id, request);
        return ApiResponse.success("Cập nhật hồ sơ thành công", response);
    }

    @DeleteMapping("/api/patients/{patientId}/health-records/{id}")
    @Operation(summary = "Delete a health record")
    public ApiResponse<Void> delete(@PathVariable UUID patientId, @PathVariable UUID id) {
        healthRecordService.delete(patientId, id);
        return ApiResponse.success(null);
    }

    @GetMapping("/api/health-records/files/{fileId}")
    @Operation(summary = "Download a health record file")
    public ResponseEntity<Resource> downloadFile(@PathVariable UUID fileId) {
        HealthRecordFile fileRecord = fileRepository.findById(fileId)
                .orElseThrow(() -> new ResourceNotFoundException("HealthRecordFile", "id", fileId));
        
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
