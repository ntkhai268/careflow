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
import java.time.LocalDate;
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

    @GetMapping("/patients/{patientId}/current")
    public ApiResponse<QueueEntryResponse> patientCurrent(
            @PathVariable UUID patientId,
            @RequestHeader(AppConstants.HEADER_USER_ID) UUID userId,
            @RequestHeader(AppConstants.HEADER_USER_ROLE) String role) {
        requireAnyRole(role, AppConstants.ROLE_PATIENT, AppConstants.ROLE_DOCTOR,
                AppConstants.ROLE_STAFF, AppConstants.ROLE_ADMIN);
        return ApiResponse.success(queueService.patientCurrent(
                patientId, userId, !AppConstants.ROLE_PATIENT.equals(role)));
    }

    @GetMapping("/tickets/appointment/{appointmentId}")
    public ApiResponse<VisitTicketResponse> ticket(
            @PathVariable UUID appointmentId,
            @RequestHeader(AppConstants.HEADER_USER_ID) UUID userId,
            @RequestHeader(AppConstants.HEADER_USER_ROLE) String role) {
        requireAnyRole(role, AppConstants.ROLE_PATIENT, AppConstants.ROLE_DOCTOR,
                AppConstants.ROLE_STAFF, AppConstants.ROLE_ADMIN);
        boolean clinicalStaff = !AppConstants.ROLE_PATIENT.equals(role);
        return ApiResponse.success(queueService.ticket(appointmentId, userId, clinicalStaff));
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
        requireAnyRole(role, AppConstants.ROLE_STAFF, AppConstants.ROLE_ADMIN);
        return ApiResponse.success("Check-in thành công", queueService.checkIn(request, userId, correlationId));
    }

    @GetMapping("/rooms/{roomId}/active")
    public ApiResponse<QueueDashboardResponse> roomDashboard(
            @PathVariable String roomId,
            @RequestHeader(AppConstants.HEADER_USER_ID) UUID userId,
            @RequestHeader(AppConstants.HEADER_USER_ROLE) String role) {
        requireAnyRole(role, AppConstants.ROLE_DOCTOR, AppConstants.ROLE_STAFF, AppConstants.ROLE_ADMIN);
        return ApiResponse.success(queueService.roomDashboard(roomId, userId, role));
    }

    @PostMapping("/rooms/{roomId}/call-next")
    public ResponseEntity<ApiResponse<QueueEntryResponse>> callNextInRoom(
            @PathVariable String roomId,
            @RequestHeader(AppConstants.HEADER_USER_ID) UUID userId,
            @RequestHeader(AppConstants.HEADER_USER_ROLE) String role,
            @RequestHeader(value = AppConstants.HEADER_IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @RequestHeader(value = AppConstants.HEADER_CORRELATION_ID, required = false) String correlationId) {
        requireAnyRole(role, AppConstants.ROLE_DOCTOR, AppConstants.ROLE_ADMIN);
        Optional<QueueEntryResponse> result = queueService.callNextInRoom(
                roomId, userId, role, idempotencyKey, correlationId);
        return result.map(entry -> ResponseEntity.ok(ApiResponse.success("Đã gọi bệnh nhân tiếp theo", entry)))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping("/entries/{entryId}/call")
    public ApiResponse<QueueEntryResponse> call(
            @PathVariable UUID entryId,
            @RequestHeader(AppConstants.HEADER_USER_ID) UUID userId,
            @RequestHeader(AppConstants.HEADER_USER_ROLE) String role,
            @RequestHeader(value = AppConstants.HEADER_IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @RequestHeader(value = AppConstants.HEADER_CORRELATION_ID, required = false) String correlationId) {
        requireAnyRole(role, AppConstants.ROLE_DOCTOR, AppConstants.ROLE_STAFF,
                AppConstants.ROLE_LAB_TECHNICIAN, AppConstants.ROLE_ADMIN);
        return ApiResponse.success("Đã gọi bệnh nhân", queueService.call(
                entryId, userId, role, idempotencyKey, correlationId));
    }

    @GetMapping("/service-points/{servicePointId}/active")
    public ApiResponse<ServicePointQueueResponse> servicePointDashboard(
            @PathVariable String servicePointId,
            @RequestParam(required = false) LocalDate date,
            @RequestHeader(AppConstants.HEADER_USER_ID) UUID userId,
            @RequestHeader(AppConstants.HEADER_USER_ROLE) String role) {
        requireAnyRole(role, AppConstants.ROLE_STAFF, AppConstants.ROLE_LAB_TECHNICIAN, AppConstants.ROLE_ADMIN);
        return ApiResponse.success(queueService.servicePointDashboard(servicePointId, date, userId, role));
    }

    @PostMapping("/service-points/{servicePointId}/call-next")
    public ResponseEntity<ApiResponse<QueueEntryResponse>> callNextAtServicePoint(
            @PathVariable String servicePointId,
            @RequestHeader(AppConstants.HEADER_USER_ID) UUID userId,
            @RequestHeader(AppConstants.HEADER_USER_ROLE) String role,
            @RequestHeader(value = AppConstants.HEADER_IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @RequestHeader(value = AppConstants.HEADER_CORRELATION_ID, required = false) String correlationId) {
        requireAnyRole(role, AppConstants.ROLE_STAFF, AppConstants.ROLE_LAB_TECHNICIAN, AppConstants.ROLE_ADMIN);
        Optional<QueueEntryResponse> result = queueService.callNextAtServicePoint(
                servicePointId, userId, role, idempotencyKey, correlationId);
        return result.map(entry -> ResponseEntity.ok(ApiResponse.success("Đã gọi lượt tiếp theo", entry)))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/departments/{departmentId}/dashboard")
    public ApiResponse<QueueDashboardResponse> dashboard(@PathVariable UUID departmentId,
                                                         @RequestHeader(AppConstants.HEADER_USER_ROLE) String role) {
        requireAnyRole(role, AppConstants.ROLE_DOCTOR, AppConstants.ROLE_ADMIN);
        return ApiResponse.success(queueService.dashboard(departmentId));
    }

    @PostMapping("/departments/{departmentId}/next")
    public ResponseEntity<ApiResponse<QueueEntryResponse>> next(@PathVariable UUID departmentId,
                                                                @RequestHeader(AppConstants.HEADER_USER_ID) UUID userId,
                                                                @RequestHeader(AppConstants.HEADER_USER_ROLE) String role,
                                                                @RequestHeader(value = AppConstants.HEADER_IDEMPOTENCY_KEY, required = false) String idempotencyKey,
                                                                @RequestHeader(value = AppConstants.HEADER_CORRELATION_ID, required = false) String correlationId) {
        requireAnyRole(role, AppConstants.ROLE_DOCTOR, AppConstants.ROLE_ADMIN);
        Optional<QueueEntryResponse> result = queueService.callNext(
                departmentId, userId, idempotencyKey, correlationId);
        return result.map(entry -> ResponseEntity.ok(ApiResponse.success("Đã gọi bệnh nhân tiếp theo", entry)))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping("/entries/{entryId}/recall")
    public ApiResponse<QueueEntryResponse> recall(@PathVariable UUID entryId,
                                                  @RequestHeader(AppConstants.HEADER_USER_ID) UUID userId,
                                                  @RequestHeader(AppConstants.HEADER_USER_ROLE) String role,
                                                  @RequestHeader(value = AppConstants.HEADER_CORRELATION_ID, required = false) String correlationId) {
        requireAnyRole(role, AppConstants.ROLE_DOCTOR, AppConstants.ROLE_STAFF,
                AppConstants.ROLE_LAB_TECHNICIAN, AppConstants.ROLE_ADMIN);
        return ApiResponse.success(queueService.recall(entryId, userId, role, correlationId));
    }

    @PostMapping("/entries/{entryId}/miss")
    public ApiResponse<QueueEntryResponse> miss(@PathVariable UUID entryId,
                                                @RequestHeader(AppConstants.HEADER_USER_ID) UUID userId,
                                                @RequestHeader(AppConstants.HEADER_USER_ROLE) String role,
                                                @RequestHeader(value = AppConstants.HEADER_CORRELATION_ID, required = false) String correlationId) {
        requireAnyRole(role, AppConstants.ROLE_DOCTOR, AppConstants.ROLE_STAFF,
                AppConstants.ROLE_LAB_TECHNICIAN, AppConstants.ROLE_ADMIN);
        return ApiResponse.success(queueService.miss(entryId, userId, role, correlationId));
    }

    @PostMapping("/entries/{entryId}/requeue")
    public ApiResponse<QueueEntryResponse> requeue(@PathVariable UUID entryId,
                                                   @RequestBody(required = false) RequeueRequest request,
                                                   @RequestHeader(AppConstants.HEADER_USER_ID) UUID userId,
                                                   @RequestHeader(AppConstants.HEADER_USER_ROLE) String role,
                                                   @RequestHeader(value = AppConstants.HEADER_CORRELATION_ID, required = false) String correlationId) {
        requireAnyRole(role, AppConstants.ROLE_DOCTOR, AppConstants.ROLE_STAFF,
                AppConstants.ROLE_LAB_TECHNICIAN, AppConstants.ROLE_ADMIN);
        return ApiResponse.success(queueService.requeue(entryId, request, userId, role, correlationId));
    }

    @PostMapping("/entries/{entryId}/start")
    public ApiResponse<QueueEntryResponse> start(@PathVariable UUID entryId,
                                                 @RequestHeader(AppConstants.HEADER_USER_ID) UUID userId,
                                                 @RequestHeader(AppConstants.HEADER_USER_ROLE) String role,
                                                 @RequestHeader(value = AppConstants.HEADER_CORRELATION_ID, required = false) String correlationId) {
        requireAnyRole(role, AppConstants.ROLE_DOCTOR, AppConstants.ROLE_STAFF,
                AppConstants.ROLE_LAB_TECHNICIAN, AppConstants.ROLE_ADMIN);
        return ApiResponse.success(queueService.start(entryId, userId, role, correlationId));
    }

    @PostMapping("/entries/{entryId}/complete")
    public ApiResponse<QueueEntryResponse> complete(@PathVariable UUID entryId,
                                                    @RequestHeader(AppConstants.HEADER_USER_ID) UUID userId,
                                                    @RequestHeader(AppConstants.HEADER_USER_ROLE) String role,
                                                    @RequestHeader(value = AppConstants.HEADER_CORRELATION_ID, required = false) String correlationId) {
        requireAnyRole(role, AppConstants.ROLE_DOCTOR, AppConstants.ROLE_STAFF,
                AppConstants.ROLE_LAB_TECHNICIAN, AppConstants.ROLE_ADMIN);
        return ApiResponse.success(queueService.complete(entryId, userId, role, correlationId));
    }

    @GetMapping("/prescriptions/{prescriptionId}/current")
    public ApiResponse<QueueEntryResponse> currentPharmacyEntry(
            @PathVariable UUID prescriptionId,
            @RequestHeader(AppConstants.HEADER_USER_ID) UUID userId,
            @RequestHeader(AppConstants.HEADER_USER_ROLE) String role) {
        requireAnyRole(role, AppConstants.ROLE_STAFF, AppConstants.ROLE_ADMIN);
        return ApiResponse.success(queueService.currentPharmacyEntry(prescriptionId, userId, role));
    }

    @PostMapping("/prescriptions/{prescriptionId}/complete")
    public ApiResponse<Void> completePharmacyEntry(
            @PathVariable UUID prescriptionId,
            @RequestHeader(AppConstants.HEADER_USER_ID) UUID userId,
            @RequestHeader(AppConstants.HEADER_USER_ROLE) String role,
            @RequestHeader(value = AppConstants.HEADER_CORRELATION_ID, required = false) String correlationId) {
        requireAnyRole(role, AppConstants.ROLE_STAFF, AppConstants.ROLE_ADMIN);
        queueService.completePharmacyEntry(prescriptionId, userId, role, java.time.Instant.now(), correlationId);
        return ApiResponse.success("Đã xác nhận phát thuốc", null);
    }

    @GetMapping("/lab-orders/{labOrderId}/current")
    public ApiResponse<QueueEntryResponse> currentLabExecution(
            @PathVariable UUID labOrderId,
            @RequestHeader(AppConstants.HEADER_USER_ID) UUID userId,
            @RequestHeader(AppConstants.HEADER_USER_ROLE) String role) {
        requireAnyRole(role, AppConstants.ROLE_LAB_TECHNICIAN, AppConstants.ROLE_STAFF,
                AppConstants.ROLE_ADMIN);
        return ApiResponse.success(queueService.currentLabExecution(labOrderId, userId, role));
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
