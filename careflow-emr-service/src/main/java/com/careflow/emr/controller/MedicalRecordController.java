package com.careflow.emr.controller;

import com.careflow.common.dto.ApiResponse;
import com.careflow.emr.dto.request.CreateMedicalRecordRequest;
import com.careflow.emr.dto.request.UpdateMedicalRecordRequest;
import com.careflow.emr.dto.response.MedicalRecordResponse;
import com.careflow.emr.dto.response.PatientSummaryResponse;
import com.careflow.emr.service.MedicalRecordService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/emr")
@RequiredArgsConstructor
@Tag(name = "EMR", description = "API Hồ sơ bệnh án điện tử liên thông (Master Health Records)")
public class MedicalRecordController {

    private final MedicalRecordService medicalRecordService;

    @PostMapping("/records")
    @Operation(summary = "Tạo hồ sơ bệnh án chủ", description = "Tạo mới hồ sơ EMR Master cho bệnh nhân")
    public ResponseEntity<ApiResponse<MedicalRecordResponse>> createMedicalRecord(
            @Valid @RequestBody CreateMedicalRecordRequest request) {
        MedicalRecordResponse response = medicalRecordService.createMedicalRecord(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Tạo hồ sơ EMR thành công", response));
    }

    @GetMapping("/patients/{patientId}/record")
    @Operation(summary = "Xem hồ sơ bệnh án chủ", description = "Lấy thông tin nhóm máu, tiền sử bệnh chủ theo patientId")
    public ResponseEntity<ApiResponse<MedicalRecordResponse>> getMedicalRecordByPatientId(
            @PathVariable UUID patientId) {
        MedicalRecordResponse response = medicalRecordService.getMedicalRecordByPatientId(patientId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PutMapping("/patients/{patientId}/record")
    @Operation(summary = "Cập nhật hồ sơ bệnh án chủ", description = "Cập nhật nhóm máu, tiền sử bệnh chủ theo patientId")
    public ResponseEntity<ApiResponse<MedicalRecordResponse>> updateMedicalRecord(
            @PathVariable UUID patientId,
            @Valid @RequestBody UpdateMedicalRecordRequest request) {
        MedicalRecordResponse response = medicalRecordService.updateMedicalRecord(patientId, request);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật hồ sơ EMR thành công", response));
    }

    @GetMapping("/patients/{patientId}/summary")
    @Operation(summary = "Tra cứu EMR tổng hợp (UC6)", description = "Tổng hợp toàn bộ hồ sơ bệnh nhân, dị ứng, EMR master, lịch sử khám và đơn thuốc")
    public ResponseEntity<ApiResponse<PatientSummaryResponse>> getPatientSummary(
            @PathVariable UUID patientId) {
        PatientSummaryResponse response = medicalRecordService.getPatientSummary(patientId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
