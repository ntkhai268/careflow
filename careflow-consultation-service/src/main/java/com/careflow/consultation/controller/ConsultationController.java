package com.careflow.consultation.controller;

import com.careflow.common.dto.ApiResponse;
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
            @Valid @RequestBody CreateConsultationRequest request) {
        ConsultationResponse response = consultationService.createConsultation(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Consultation created successfully", response));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ConsultationResponse>> getConsultation(@PathVariable UUID id) {
        ConsultationResponse response = consultationService.getConsultation(id);
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

    @PutMapping("/{id}/status")
    public ResponseEntity<ApiResponse<ConsultationResponse>> updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateConsultationStatusRequest request) {
        ConsultationResponse response = consultationService.updateStatus(id, request);
        return ResponseEntity.ok(ApiResponse.success("Consultation status updated successfully", response));
    }

    @PutMapping("/{id}/complete")
    public ResponseEntity<ApiResponse<ConsultationResponse>> completeConsultation(@PathVariable UUID id) {
        ConsultationResponse response = consultationService.completeConsultation(id);
        return ResponseEntity.ok(ApiResponse.success("Consultation completed successfully", response));
    }

    @GetMapping("/patient/{patientId}")
    public ResponseEntity<ApiResponse<List<ConsultationResponse>>> getByPatient(@PathVariable UUID patientId) {
        List<ConsultationResponse> responses = consultationService.getConsultationsByPatient(patientId);
        return ResponseEntity.ok(ApiResponse.success(responses));
    }

    @GetMapping("/doctor/{doctorId}")
    public ResponseEntity<ApiResponse<List<ConsultationResponse>>> getByDoctor(@PathVariable UUID doctorId) {
        List<ConsultationResponse> responses = consultationService.getConsultationsByDoctor(doctorId);
        return ResponseEntity.ok(ApiResponse.success(responses));
    }

    @GetMapping("/doctor/{doctorId}/today")
    public ResponseEntity<ApiResponse<List<ConsultationResponse>>> getTodayByDoctor(@PathVariable UUID doctorId) {
        List<ConsultationResponse> responses = consultationService.getTodayConsultationsByDoctor(doctorId);
        return ResponseEntity.ok(ApiResponse.success(responses));
    }

    @GetMapping("/appointment/{appointmentId}")
    public ResponseEntity<ApiResponse<List<ConsultationResponse>>> getByAppointment(@PathVariable UUID appointmentId) {
        List<ConsultationResponse> responses = consultationService.getConsultationsByAppointment(appointmentId);
        return ResponseEntity.ok(ApiResponse.success(responses));
    }
}
