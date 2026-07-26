package com.careflow.queue.controller;

import com.careflow.common.constants.AppConstants;
import com.careflow.common.dto.ApiResponse;
import com.careflow.common.exception.BusinessException;
import com.careflow.queue.dto.*;
import com.careflow.queue.service.QueueManagementService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/queues")
public class QueueController {
    private final QueueManagementService queueService;

    public QueueController(QueueManagementService queueService) {
        this.queueService = queueService;
    }

    @GetMapping("/me/status")
    public ApiResponse<QueueEntryResponse> myStatus(@RequestHeader(AppConstants.HEADER_USER_ID) UUID userId,
                                                    @RequestHeader(AppConstants.HEADER_USER_ROLE) String role) {
        requireRole(role, AppConstants.ROLE_PATIENT);
        return ApiResponse.success(queueService.myStatus(userId));
    }

    @PostMapping("/entries")
    public ResponseEntity<ApiResponse<QueueEntryResponse>> manualIntake(
            @Valid @RequestBody ManualIntakeRequest request,
            @RequestHeader(AppConstants.HEADER_USER_ROLE) String role,
            @RequestHeader(value = AppConstants.HEADER_IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @RequestHeader(value = AppConstants.HEADER_CORRELATION_ID, required = false) String correlationId) {
        requireRole(role, AppConstants.ROLE_ADMIN);
        QueueEntryResponse entry = queueService.manualIntake(request, idempotencyKey, correlationId);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.<QueueEntryResponse>builder()
                .status(201).message("Tiếp nhận thành công").data(entry).build());
    }

    @GetMapping("/appointments/{appointmentId}/qr")
    public ApiResponse<Map<String, String>> qr(@PathVariable UUID appointmentId,
                                               @RequestHeader(AppConstants.HEADER_USER_ID) UUID userId,
                                               @RequestHeader(AppConstants.HEADER_USER_ROLE) String role) {
        requireRole(role, AppConstants.ROLE_PATIENT);
        return ApiResponse.success(Map.of("qrToken", queueService.issueQr(appointmentId, userId)));
    }

    @PostMapping("/check-in")
    public ApiResponse<QueueEntryResponse> checkIn(@Valid @RequestBody CheckInRequest request,
                                                   @RequestHeader(AppConstants.HEADER_USER_ID) UUID userId,
                                                   @RequestHeader(AppConstants.HEADER_USER_ROLE) String role,
                                                   @RequestHeader(value = AppConstants.HEADER_CORRELATION_ID, required = false) String correlationId) {
        requireRole(role, AppConstants.ROLE_PATIENT);
        return ApiResponse.success("Check-in thành công", queueService.checkIn(request.qrToken(), userId, correlationId));
    }

    @GetMapping("/departments/{departmentId}/dashboard")
    public ApiResponse<QueueDashboardResponse> dashboard(@PathVariable UUID departmentId,
                                                         @RequestHeader(AppConstants.HEADER_USER_ROLE) String role) {
        requireAnyRole(role, AppConstants.ROLE_DOCTOR, AppConstants.ROLE_ADMIN);
        return ApiResponse.success(queueService.dashboard(departmentId));
    }

    @PostMapping("/departments/{departmentId}/next")
    public ResponseEntity<ApiResponse<QueueEntryResponse>> next(@PathVariable UUID departmentId,
                                                                @RequestHeader(AppConstants.HEADER_USER_ROLE) String role,
                                                                @RequestHeader(value = AppConstants.HEADER_IDEMPOTENCY_KEY, required = false) String idempotencyKey,
                                                                @RequestHeader(value = AppConstants.HEADER_CORRELATION_ID, required = false) String correlationId) {
        requireAnyRole(role, AppConstants.ROLE_DOCTOR, AppConstants.ROLE_ADMIN);
        Optional<QueueEntryResponse> result = queueService.callNext(departmentId, idempotencyKey, correlationId);
        return result.map(entry -> ResponseEntity.ok(ApiResponse.success("Đã gọi bệnh nhân tiếp theo", entry)))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping("/entries/{entryId}/recall")
    public ApiResponse<QueueEntryResponse> recall(@PathVariable UUID entryId,
                                                  @RequestHeader(AppConstants.HEADER_USER_ROLE) String role,
                                                  @RequestHeader(value = AppConstants.HEADER_CORRELATION_ID, required = false) String correlationId) {
        requireAnyRole(role, AppConstants.ROLE_DOCTOR, AppConstants.ROLE_ADMIN);
        return ApiResponse.success(queueService.recall(entryId, correlationId));
    }

    @PostMapping("/entries/{entryId}/miss")
    public ApiResponse<QueueEntryResponse> miss(@PathVariable UUID entryId,
                                                @RequestHeader(AppConstants.HEADER_USER_ROLE) String role,
                                                @RequestHeader(value = AppConstants.HEADER_CORRELATION_ID, required = false) String correlationId) {
        requireAnyRole(role, AppConstants.ROLE_DOCTOR, AppConstants.ROLE_ADMIN);
        return ApiResponse.success(queueService.miss(entryId, correlationId));
    }

    @PostMapping("/entries/{entryId}/requeue")
    public ApiResponse<QueueEntryResponse> requeue(@PathVariable UUID entryId,
                                                   @RequestBody(required = false) RequeueRequest request,
                                                   @RequestHeader(AppConstants.HEADER_USER_ROLE) String role,
                                                   @RequestHeader(value = AppConstants.HEADER_CORRELATION_ID, required = false) String correlationId) {
        requireAnyRole(role, AppConstants.ROLE_DOCTOR, AppConstants.ROLE_ADMIN);
        return ApiResponse.success(queueService.requeue(entryId, request, correlationId));
    }

    @PostMapping("/entries/{entryId}/start")
    public ApiResponse<QueueEntryResponse> start(@PathVariable UUID entryId,
                                                 @RequestHeader(AppConstants.HEADER_USER_ROLE) String role,
                                                 @RequestHeader(value = AppConstants.HEADER_CORRELATION_ID, required = false) String correlationId) {
        requireAnyRole(role, AppConstants.ROLE_DOCTOR, AppConstants.ROLE_ADMIN);
        return ApiResponse.success(queueService.start(entryId, correlationId));
    }

    @PostMapping("/entries/{entryId}/complete")
    public ApiResponse<QueueEntryResponse> complete(@PathVariable UUID entryId,
                                                    @RequestHeader(AppConstants.HEADER_USER_ROLE) String role,
                                                    @RequestHeader(value = AppConstants.HEADER_CORRELATION_ID, required = false) String correlationId) {
        requireAnyRole(role, AppConstants.ROLE_DOCTOR, AppConstants.ROLE_ADMIN);
        return ApiResponse.success(queueService.complete(entryId, correlationId));
    }

    @GetMapping("/configs/{departmentId}")
    public ApiResponse<QueueConfigResponse> getConfig(@PathVariable UUID departmentId,
                                                      @RequestHeader(AppConstants.HEADER_USER_ROLE) String role) {
        requireRole(role, AppConstants.ROLE_ADMIN);
        return ApiResponse.success(queueService.getConfig(departmentId));
    }

    @PutMapping("/configs/{departmentId}")
    public ApiResponse<QueueConfigResponse> saveConfig(@PathVariable UUID departmentId,
                                                       @Valid @RequestBody QueueConfigRequest request,
                                                       @RequestHeader(AppConstants.HEADER_USER_ROLE) String role) {
        requireRole(role, AppConstants.ROLE_ADMIN);
        return ApiResponse.success(queueService.saveConfig(departmentId, request));
    }

    private void requireRole(String actual, String expected) {
        if (!expected.equals(actual)) throw new BusinessException(403, "Không có quyền thực hiện thao tác này");
    }

    private void requireAnyRole(String actual, String... accepted) {
        if (!Set.of(accepted).contains(actual)) throw new BusinessException(403, "Không có quyền thực hiện thao tác này");
    }
}
