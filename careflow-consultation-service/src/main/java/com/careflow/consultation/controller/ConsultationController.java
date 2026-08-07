package com.careflow.consultation.controller;

import com.careflow.common.dto.ApiResponse;
import com.careflow.common.constants.AppConstants;
import com.careflow.consultation.dto.request.CreateConsultationRequest;
import com.careflow.consultation.dto.request.UpdateConsultationRequest;
import com.careflow.consultation.dto.request.UpdateConsultationStatusRequest;
import com.careflow.consultation.dto.response.ConsultationResponse;
import com.careflow.consultation.service.ConsultationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/consultations")
@RequiredArgsConstructor
public class ConsultationController {

    private final ConsultationService consultationService;

    @PostMapping
    public ResponseEntity<ApiResponse<ConsultationResponse>> createConsultation(
            @Valid @RequestBody CreateConsultationRequest request,
            @RequestHeader(value = AppConstants.HEADER_USER_ID, required = false) UUID actorUserId,
            @RequestHeader(value = AppConstants.HEADER_USER_ROLE, required = false) String actorRole) {
        ConsultationResponse response = actorUserId == null
                ? consultationService.createConsultation(request)
                : consultationService.createConsultation(request, actorUserId, actorRole);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Consultation created successfully", response));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ConsultationResponse>> getConsultation(
            @PathVariable UUID id,
            @RequestHeader(value = AppConstants.HEADER_USER_ID, required = false) UUID actorUserId,
            @RequestHeader(value = AppConstants.HEADER_USER_ROLE, required = false) String actorRole) {
        ConsultationResponse response = consultationService.getConsultation(id, actorUserId, actorRole);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/{id}/status")
    public ResponseEntity<ApiResponse<String>> getConsultationStatus(@PathVariable UUID id) {
        String status = consultationService.getConsultationStatus(id);
        return ResponseEntity.ok(ApiResponse.success("Success", status));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ConsultationResponse>> updateConsultation(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateConsultationRequest request) {
        ConsultationResponse response = consultationService.updateConsultation(id, request);
        return ResponseEntity.ok(ApiResponse.success("Consultation updated successfully", response));
    }

    @PutMapping("/{id}/clinical-data")
    public ResponseEntity<ApiResponse<ConsultationResponse>> updateClinicalData(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateConsultationRequest request,
            @RequestHeader(value = AppConstants.HEADER_USER_ID, required = false) UUID actorUserId,
            @RequestHeader(value = AppConstants.HEADER_USER_ROLE, required = false) String actorRole) {
        return ResponseEntity.ok(ApiResponse.success("Clinical data updated",
                consultationService.updateClinicalData(id, request, actorUserId, actorRole)));
    }

    @PostMapping("/{id}/wait-for-results")
    public ResponseEntity<ApiResponse<ConsultationResponse>> waitForResults(
            @PathVariable UUID id,
            @RequestHeader(value = AppConstants.HEADER_USER_ID, required = false) UUID actorUserId,
            @RequestHeader(value = AppConstants.HEADER_USER_ROLE, required = false) String actorRole) {
        return ResponseEntity.ok(ApiResponse.success("Consultation is waiting for results",
                consultationService.waitForResults(id, actorUserId, actorRole)));
    }

    @PostMapping("/{id}/resume")
    public ResponseEntity<ApiResponse<ConsultationResponse>> resume(
            @PathVariable UUID id,
            @RequestHeader(value = AppConstants.HEADER_USER_ID, required = false) UUID actorUserId,
            @RequestHeader(value = AppConstants.HEADER_USER_ROLE, required = false) String actorRole) {
        return ResponseEntity.ok(ApiResponse.success("Consultation resumed",
                consultationService.resume(id, actorUserId, actorRole)));
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<ApiResponse<ConsultationResponse>> updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateConsultationStatusRequest request) {
        ConsultationResponse response = consultationService.updateStatus(id, request);
        return ResponseEntity.ok(ApiResponse.success("Consultation status updated successfully", response));
    }

    @PostMapping("/{id}/complete")
    public ResponseEntity<ApiResponse<ConsultationResponse>> completeConsultation(
            @PathVariable UUID id,
            @RequestHeader(value = AppConstants.HEADER_USER_ID, required = false) UUID actorUserId,
            @RequestHeader(value = AppConstants.HEADER_USER_ROLE, required = false) String actorRole) {
        ConsultationResponse response = actorUserId == null
                ? consultationService.completeConsultation(id)
                : consultationService.completeConsultation(id, actorUserId, actorRole);
        return ResponseEntity.ok(ApiResponse.success("Consultation completed successfully", response));
    }

    @PutMapping("/{id}/complete")
    public ResponseEntity<ApiResponse<ConsultationResponse>> completeConsultationLegacy(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Consultation completed successfully",
                consultationService.completeConsultation(id)));
    }

    @GetMapping("/patient/{patientId}")
    public ResponseEntity<ApiResponse<List<ConsultationResponse>>> getByPatient(
            @PathVariable UUID patientId,
            @RequestHeader(value = AppConstants.HEADER_USER_ID, required = false) UUID actorUserId,
            @RequestHeader(value = AppConstants.HEADER_USER_ROLE, required = false) String actorRole) {
        List<ConsultationResponse> responses = consultationService.getConsultationsByPatient(
                patientId, actorUserId, actorRole);
        return ResponseEntity.ok(ApiResponse.success(responses));
    }

    @GetMapping("/doctor/{doctorId}")
    public ResponseEntity<ApiResponse<List<ConsultationResponse>>> getByDoctor(
            @PathVariable UUID doctorId,
            @RequestHeader(value = AppConstants.HEADER_USER_ID, required = false) UUID actorUserId,
            @RequestHeader(value = AppConstants.HEADER_USER_ROLE, required = false) String actorRole) {
        List<ConsultationResponse> responses = consultationService.getConsultationsByDoctor(
                doctorId, actorUserId, actorRole);
        return ResponseEntity.ok(ApiResponse.success(responses));
    }

    @GetMapping("/doctor/{doctorId}/today")
    public ResponseEntity<ApiResponse<List<ConsultationResponse>>> getTodayByDoctor(
            @PathVariable UUID doctorId,
            @RequestHeader(value = AppConstants.HEADER_USER_ID, required = false) UUID actorUserId,
            @RequestHeader(value = AppConstants.HEADER_USER_ROLE, required = false) String actorRole) {
        List<ConsultationResponse> responses = consultationService.getTodayConsultationsByDoctor(
                doctorId, actorUserId, actorRole);
        return ResponseEntity.ok(ApiResponse.success(responses));
    }

    @GetMapping("/appointment/{appointmentId}")
    public ResponseEntity<ApiResponse<List<ConsultationResponse>>> getByAppointment(
            @PathVariable UUID appointmentId,
            @RequestHeader(value = AppConstants.HEADER_USER_ID, required = false) UUID actorUserId,
            @RequestHeader(value = AppConstants.HEADER_USER_ROLE, required = false) String actorRole) {
        List<ConsultationResponse> responses = consultationService.getConsultationsByAppointment(
                appointmentId, actorUserId, actorRole);
        return ResponseEntity.ok(ApiResponse.success(responses));
    }
}
