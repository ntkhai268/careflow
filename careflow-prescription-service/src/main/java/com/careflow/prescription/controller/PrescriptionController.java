package com.careflow.prescription.controller;

import com.careflow.common.dto.ApiResponse;
import com.careflow.common.constants.AppConstants;
import com.careflow.prescription.dto.request.CreatePrescriptionRequest;
import com.careflow.prescription.dto.request.AmendPrescriptionRequest;
import com.careflow.prescription.dto.response.PrescriptionResponse;
import com.careflow.prescription.service.PrescriptionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/prescriptions")
@RequiredArgsConstructor
public class PrescriptionController {

    private final PrescriptionService prescriptionService;

    @PostMapping
    public ResponseEntity<ApiResponse<PrescriptionResponse>> createPrescription(
            @Valid @RequestBody CreatePrescriptionRequest request,
            @RequestHeader(value = AppConstants.HEADER_USER_ID, required = false) UUID actorUserId,
            @RequestHeader(value = AppConstants.HEADER_USER_ROLE, required = false) String actorRole) {
        PrescriptionResponse response = prescriptionService.createPrescription(request, actorUserId, actorRole);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Prescription created successfully", response));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PrescriptionResponse>> getPrescription(
            @PathVariable UUID id,
            @RequestHeader(value = AppConstants.HEADER_USER_ID, required = false) UUID actorUserId,
            @RequestHeader(value = AppConstants.HEADER_USER_ROLE, required = false) String actorRole) {
        PrescriptionResponse response = prescriptionService.getPrescription(id, actorUserId, actorRole);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<PrescriptionResponse>> updatePrescription(
            @PathVariable UUID id,
            @Valid @RequestBody CreatePrescriptionRequest request,
            @RequestHeader(value = AppConstants.HEADER_USER_ID, required = false) UUID actorUserId,
            @RequestHeader(value = AppConstants.HEADER_USER_ROLE, required = false) String actorRole) {
        PrescriptionResponse response = prescriptionService.updatePrescription(id, request, actorUserId, actorRole);
        return ResponseEntity.ok(ApiResponse.success("Prescription updated successfully", response));
    }

    @PostMapping("/{id}/confirm")
    public ResponseEntity<ApiResponse<PrescriptionResponse>> confirmPrescription(
            @PathVariable UUID id,
            @RequestHeader(value = AppConstants.HEADER_USER_ID, required = false) UUID actorUserId,
            @RequestHeader(value = AppConstants.HEADER_USER_ROLE, required = false) String actorRole) {
        PrescriptionResponse response = actorUserId == null
                ? prescriptionService.confirmPrescription(id)
                : prescriptionService.confirmPrescription(id, actorUserId, actorRole);
        return ResponseEntity.ok(ApiResponse.success("Prescription confirmed successfully", response));
    }

    @PostMapping("/{id}/dispense")
    public ResponseEntity<ApiResponse<PrescriptionResponse>> dispensePrescription(
            @PathVariable UUID id,
            @RequestHeader(value = AppConstants.HEADER_USER_ID, required = false) UUID actorUserId,
            @RequestHeader(value = AppConstants.HEADER_USER_ROLE, required = false) String actorRole,
            @RequestHeader(value = AppConstants.HEADER_CORRELATION_ID, required = false) String correlationId) {
        PrescriptionResponse response = prescriptionService.dispensePrescription(
                id, actorUserId, actorRole, correlationId);
        return ResponseEntity.ok(ApiResponse.success("Prescription dispensed successfully", response));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<PrescriptionResponse>> cancelPrescription(
            @PathVariable UUID id,
            @Valid @RequestBody com.careflow.prescription.dto.request.CancelPrescriptionRequest request,
            @RequestHeader(value = AppConstants.HEADER_USER_ID, required = false) UUID actorUserId,
            @RequestHeader(value = AppConstants.HEADER_USER_ROLE, required = false) String actorRole) {
        PrescriptionResponse response = prescriptionService.cancelPrescription(
                id, request.getReason(), actorUserId, actorRole);
        return ResponseEntity.ok(ApiResponse.success("Prescription cancelled successfully", response));
    }

    @PostMapping("/{id}/amendments")
    public ResponseEntity<ApiResponse<PrescriptionResponse>> amendPrescription(
            @PathVariable UUID id,
            @Valid @RequestBody AmendPrescriptionRequest request,
            @RequestHeader(value = AppConstants.HEADER_USER_ID, required = false) UUID actorUserId,
            @RequestHeader(value = AppConstants.HEADER_USER_ROLE, required = false) String actorRole) {
        PrescriptionResponse response = prescriptionService.amendPrescription(
                id, request, actorUserId, actorRole);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Prescription amendment created successfully", response));
    }

    @PutMapping("/{id}/confirm")
    public ResponseEntity<ApiResponse<PrescriptionResponse>> confirmPrescriptionLegacy(
            @PathVariable UUID id,
            @RequestHeader(value = AppConstants.HEADER_USER_ID, required = false) UUID actorUserId,
            @RequestHeader(value = AppConstants.HEADER_USER_ROLE, required = false) String actorRole) {
        return ResponseEntity.ok(ApiResponse.success("Prescription confirmed successfully",
                prescriptionService.confirmPrescription(id, actorUserId, actorRole)));
    }

    @GetMapping("/consultation/{consultationId}")
    public ResponseEntity<ApiResponse<List<PrescriptionResponse>>> getByConsultation(
            @PathVariable UUID consultationId,
            @RequestHeader(value = AppConstants.HEADER_USER_ID, required = false) UUID actorUserId,
            @RequestHeader(value = AppConstants.HEADER_USER_ROLE, required = false) String actorRole) {
        List<PrescriptionResponse> responses = prescriptionService.getByConsultation(
                consultationId, actorUserId, actorRole);
        return ResponseEntity.ok(ApiResponse.success(responses));
    }

    @GetMapping("/patient/{patientId}")
    public ResponseEntity<ApiResponse<List<PrescriptionResponse>>> getByPatient(
            @PathVariable UUID patientId,
            @RequestHeader(value = AppConstants.HEADER_USER_ID, required = false) UUID actorUserId,
            @RequestHeader(value = AppConstants.HEADER_USER_ROLE, required = false) String actorRole) {
        List<PrescriptionResponse> responses = prescriptionService.getByPatient(
                patientId, actorUserId, actorRole);
        return ResponseEntity.ok(ApiResponse.success(responses));
    }

    @GetMapping("/medicines")
    public ResponseEntity<ApiResponse<List<PrescriptionService.MedicineInfo>>> getMedicineCatalog() {
        List<PrescriptionService.MedicineInfo> medicines = prescriptionService.getMedicineCatalog();
        return ResponseEntity.ok(ApiResponse.success(medicines));
    }
}
