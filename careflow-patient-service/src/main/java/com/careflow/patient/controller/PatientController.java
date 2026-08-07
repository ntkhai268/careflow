package com.careflow.patient.controller;

import com.careflow.common.dto.ApiResponse;
import com.careflow.common.exception.BusinessException;
import com.careflow.patient.dto.request.CreatePatientRequest;
import com.careflow.patient.dto.request.UpdatePatientRequest;
import com.careflow.patient.dto.response.PatientResponse;
import com.careflow.patient.dto.response.PatientOperationalResponse;
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
@Tag(name = "Patient", description = "Patient profile API")
public class PatientController {

    private final PatientService patientService;

    @PostMapping
    @Operation(summary = "Create a patient profile")
    public ResponseEntity<ApiResponse<PatientResponse>> createPatient(
            @Valid @RequestBody CreatePatientRequest request,
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader,
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {
        PatientResponse response = patientService.createPatient(request, parseUserId(userIdHeader), userRole);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Patient profile created", response));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a patient profile by ID")
    public ResponseEntity<ApiResponse<PatientResponse>> getPatientById(
            @PathVariable UUID id,
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader,
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {
        PatientResponse response = patientService.getPatientById(id, parseUserId(userIdHeader), userRole);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/{id}/operational-summary")
    @Operation(summary = "Get the minimum patient data needed for reception")
    public ResponseEntity<ApiResponse<PatientOperationalResponse>> getOperationalSummary(
            @PathVariable UUID id,
            @RequestParam UUID appointmentId,
            @RequestParam String roomId,
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader,
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {
        PatientOperationalResponse response = patientService.getOperationalSummary(
                id, appointmentId, roomId, parseUserId(userIdHeader), userRole);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/{id}/owner-user-id")
    public ResponseEntity<ApiResponse<UUID>> getOwnerUserId(
            @PathVariable UUID id,
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader,
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {
        UUID ownerUserId = patientService.getOwnerUserId(id, parseUserId(userIdHeader), userRole);
        return ResponseEntity.ok(ApiResponse.success(ownerUserId));
    }

    @GetMapping("/user/{userId}")
    @Operation(summary = "Find a patient profile by user ID")
    public ResponseEntity<ApiResponse<PatientResponse>> getPatientByUserId(
            @PathVariable UUID userId,
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader,
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {
        PatientResponse response = patientService.getPatientByUserId(
                userId, parseUserId(userIdHeader), userRole);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a patient profile")
    public ResponseEntity<ApiResponse<PatientResponse>> updatePatient(
            @PathVariable UUID id,
            @Valid @RequestBody UpdatePatientRequest request,
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader,
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {
        PatientResponse response = patientService.updatePatient(
                id, request, parseUserId(userIdHeader), userRole);
        return ResponseEntity.ok(ApiResponse.success("Patient profile updated", response));
    }

    private UUID parseUserId(String userIdHeader) {
        if (userIdHeader == null || userIdHeader.isBlank()) {
            throw new BusinessException(401, "Missing X-User-Id authentication header");
        }
        try {
            return UUID.fromString(userIdHeader.trim());
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(401, "Invalid X-User-Id authentication header");
        }
    }
}
