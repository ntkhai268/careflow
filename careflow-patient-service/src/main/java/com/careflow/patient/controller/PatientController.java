package com.careflow.patient.controller;

import com.careflow.common.dto.ApiResponse;
import com.careflow.patient.dto.request.CreatePatientRequest;
import com.careflow.patient.dto.request.UpdatePatientRequest;
import com.careflow.patient.dto.response.PatientResponse;
import com.careflow.patient.service.PatientService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/patients")
@RequiredArgsConstructor
@Tag(name = "Patient", description = "API quản lý hồ sơ bệnh nhân")
public class PatientController {

    private final PatientService patientService;

    @PostMapping
    @Operation(summary = "Tạo hồ sơ bệnh nhân", description = "Tạo mới hồ sơ bệnh nhân sau khi đăng ký tài khoản")
    public ResponseEntity<ApiResponse<PatientResponse>> createPatient(
            @Valid @RequestBody CreatePatientRequest request) {
        PatientResponse response = patientService.createPatient(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Tạo hồ sơ bệnh nhân thành công", response));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Xem hồ sơ bệnh nhân", description = "Xem chi tiết hồ sơ bệnh nhân theo ID (UUID)")
    public ResponseEntity<ApiResponse<PatientResponse>> getPatientById(
            @PathVariable UUID id) {
        PatientResponse response = patientService.getPatientById(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/user/{userId}")
    @Operation(summary = "Tìm bệnh nhân theo userId", description = "Tìm hồ sơ bệnh nhân theo userId từ Identity Service")
    public ResponseEntity<ApiResponse<PatientResponse>> getPatientByUserId(
            @PathVariable UUID userId) {
        PatientResponse response = patientService.getPatientByUserId(userId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Cập nhật hồ sơ bệnh nhân", description = "Cập nhật thông tin hồ sơ (partial update — chỉ cập nhật field gửi lên)")
    public ResponseEntity<ApiResponse<PatientResponse>> updatePatient(
            @PathVariable UUID id,
            @Valid @RequestBody UpdatePatientRequest request) {
        PatientResponse response = patientService.updatePatient(id, request);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật hồ sơ thành công", response));
    }
}
