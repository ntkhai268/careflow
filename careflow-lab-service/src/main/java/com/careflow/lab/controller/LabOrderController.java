package com.careflow.lab.controller;

import com.careflow.common.constants.AppConstants;
import com.careflow.common.dto.ApiResponse;
import com.careflow.lab.dto.request.CreateLabOrderRequest;
import com.careflow.lab.dto.request.UpdateLabResultRequest;
import com.careflow.lab.dto.response.LabOrderItemResponse;
import com.careflow.lab.dto.response.LabOrderResponse;
import com.careflow.lab.service.LabOrderService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/labs/orders")
public class LabOrderController {
    private final LabOrderService service;

    public LabOrderController(LabOrderService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<LabOrderResponse>> createOrder(
            @Valid @RequestBody CreateLabOrderRequest request,
            @RequestHeader(AppConstants.HEADER_USER_ID) UUID userId,
            @RequestHeader(AppConstants.HEADER_USER_ROLE) String role,
            @RequestHeader(value = AppConstants.HEADER_CORRELATION_ID, required = false) String correlationId) {
        LabOrderResponse response = service.createOrder(request, userId, role, correlationId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Lab order created successfully", response));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<LabOrderResponse>> getOrder(
            @PathVariable UUID id,
            @RequestHeader(AppConstants.HEADER_USER_ID) UUID userId,
            @RequestHeader(AppConstants.HEADER_USER_ROLE) String role) {
        return ResponseEntity.ok(ApiResponse.success(service.getOrder(id, userId, role)));
    }

    @GetMapping("/consultation/{consultationId}")
    public ResponseEntity<ApiResponse<List<LabOrderResponse>>> getByConsultation(
            @PathVariable UUID consultationId,
            @RequestHeader(AppConstants.HEADER_USER_ID) UUID userId,
            @RequestHeader(AppConstants.HEADER_USER_ROLE) String role) {
        return ResponseEntity.ok(ApiResponse.success(service.getByConsultation(consultationId, userId, role)));
    }

    @GetMapping("/patient/{patientId}")
    public ResponseEntity<ApiResponse<List<LabOrderResponse>>> getByPatient(
            @PathVariable UUID patientId,
            @RequestHeader(AppConstants.HEADER_USER_ID) UUID userId,
            @RequestHeader(AppConstants.HEADER_USER_ROLE) String role) {
        return ResponseEntity.ok(ApiResponse.success(service.getByPatient(patientId, userId, role)));
    }

    @PostMapping("/{id}/start")
    public ResponseEntity<ApiResponse<LabOrderResponse>> start(
            @PathVariable UUID id,
            @RequestHeader(AppConstants.HEADER_USER_ID) UUID userId,
            @RequestHeader(AppConstants.HEADER_USER_ROLE) String role) {
        return ResponseEntity.ok(ApiResponse.success("Lab order started", service.startOrder(id, userId, role)));
    }

    @PutMapping("/{id}/items/{itemId}/result")
    public ResponseEntity<ApiResponse<LabOrderItemResponse>> updateResult(
            @PathVariable UUID id,
            @PathVariable UUID itemId,
            @Valid @RequestBody UpdateLabResultRequest request,
            @RequestHeader(AppConstants.HEADER_USER_ID) UUID userId,
            @RequestHeader(AppConstants.HEADER_USER_ROLE) String role) {
        return ResponseEntity.ok(ApiResponse.success("Lab result saved",
                service.updateResult(id, itemId, request, userId, role)));
    }

    @PostMapping("/{id}/finalize")
    public ResponseEntity<ApiResponse<LabOrderResponse>> finalizeOrder(
            @PathVariable UUID id,
            @RequestHeader(AppConstants.HEADER_USER_ID) UUID userId,
            @RequestHeader(AppConstants.HEADER_USER_ROLE) String role,
            @RequestHeader(value = AppConstants.HEADER_CORRELATION_ID, required = false) String correlationId) {
        return ResponseEntity.ok(ApiResponse.success("Lab order finalized",
                service.finalizeOrder(id, userId, role, correlationId)));
    }

    @PostMapping("/{id}/mark-reviewed")
    public ResponseEntity<ApiResponse<LabOrderResponse>> markReviewed(
            @PathVariable UUID id,
            @RequestHeader(AppConstants.HEADER_USER_ID) UUID userId,
            @RequestHeader(AppConstants.HEADER_USER_ROLE) String role) {
        return ResponseEntity.ok(ApiResponse.success("Lab order reviewed", service.markReviewed(id, userId, role)));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<LabOrderResponse>> cancel(
            @PathVariable UUID id,
            @RequestHeader(AppConstants.HEADER_USER_ID) UUID userId,
            @RequestHeader(AppConstants.HEADER_USER_ROLE) String role) {
        return ResponseEntity.ok(ApiResponse.success("Lab order cancelled", service.cancel(id, userId, role)));
    }
}
