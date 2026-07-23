package com.careflow.prescription.controller;

import com.careflow.common.dto.ApiResponse;
import com.careflow.prescription.dto.request.CreatePrescriptionRequest;
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
            @Valid @RequestBody CreatePrescriptionRequest request) {
        PrescriptionResponse response = prescriptionService.createPrescription(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Prescription created successfully", response));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PrescriptionResponse>> getPrescription(@PathVariable UUID id) {
        PrescriptionResponse response = prescriptionService.getPrescription(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<PrescriptionResponse>> updatePrescription(
            @PathVariable UUID id,
            @Valid @RequestBody CreatePrescriptionRequest request) {
        PrescriptionResponse response = prescriptionService.updatePrescription(id, request);
        return ResponseEntity.ok(ApiResponse.success("Prescription updated successfully", response));
    }

    @PutMapping("/{id}/confirm")
    public ResponseEntity<ApiResponse<PrescriptionResponse>> confirmPrescription(@PathVariable UUID id) {
        PrescriptionResponse response = prescriptionService.confirmPrescription(id);
        return ResponseEntity.ok(ApiResponse.success("Prescription confirmed successfully", response));
    }

    @GetMapping("/consultation/{consultationId}")
    public ResponseEntity<ApiResponse<List<PrescriptionResponse>>> getByConsultation(
            @PathVariable UUID consultationId) {
        List<PrescriptionResponse> responses = prescriptionService.getByConsultation(consultationId);
        return ResponseEntity.ok(ApiResponse.success(responses));
    }

    @GetMapping("/patient/{patientId}")
    public ResponseEntity<ApiResponse<List<PrescriptionResponse>>> getByPatient(@PathVariable UUID patientId) {
        List<PrescriptionResponse> responses = prescriptionService.getByPatient(patientId);
        return ResponseEntity.ok(ApiResponse.success(responses));
    }

    @GetMapping("/medicines")
    public ResponseEntity<ApiResponse<List<PrescriptionService.MedicineInfo>>> getMedicineCatalog() {
        List<PrescriptionService.MedicineInfo> medicines = prescriptionService.getMedicineCatalog();
        return ResponseEntity.ok(ApiResponse.success(medicines));
    }
}
