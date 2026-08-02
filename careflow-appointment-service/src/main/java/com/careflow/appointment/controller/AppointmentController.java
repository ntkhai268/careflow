package com.careflow.appointment.controller;

import com.careflow.appointment.dto.request.CreateAppointmentRequest;
import com.careflow.appointment.dto.request.UpdateAppointmentStatusRequest;
import com.careflow.appointment.dto.response.AppointmentResponse;
import com.careflow.appointment.model.Department;
import com.careflow.appointment.service.AppointmentService;
import com.careflow.common.dto.ApiResponse;
import com.careflow.common.constants.AppConstants;
import com.careflow.common.exception.BusinessException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/appointments")
@RequiredArgsConstructor
@Tag(name = "Appointment", description = "API quản lý đặt lịch khám bệnh")
public class AppointmentController {

    private final AppointmentService appointmentService;

    @PostMapping
    @Operation(summary = "Đặt lịch khám", description = "Tạo lịch hẹn khám bệnh mới")
    public ResponseEntity<ApiResponse<AppointmentResponse>> createAppointment(
            @Valid @RequestBody CreateAppointmentRequest request,
            @RequestHeader(AppConstants.HEADER_USER_ID) UUID userId,
            @RequestHeader(AppConstants.HEADER_USER_ROLE) String role,
            @RequestHeader(value = AppConstants.HEADER_CORRELATION_ID, required = false) String correlationId) {
        if (!List.of(AppConstants.ROLE_PATIENT, AppConstants.ROLE_ADMIN).contains(role)) {
            throw new BusinessException(403, "Không có quyền đặt lịch khám");
        }
        AppointmentResponse response = appointmentService.createAppointment(request, userId, correlationId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Đặt lịch khám thành công", response));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Xem chi tiết lịch khám")
    public ResponseEntity<ApiResponse<AppointmentResponse>> getAppointmentById(
            @PathVariable UUID id,
            @RequestHeader(AppConstants.HEADER_USER_ID) UUID userId,
            @RequestHeader(AppConstants.HEADER_USER_ROLE) String role) {
        AppointmentResponse response = appointmentService.getAppointmentById(id, userId, role);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/patient/{patientId}")
    @Operation(summary = "Danh sách lịch khám của bệnh nhân")
    public ResponseEntity<ApiResponse<List<AppointmentResponse>>> getAppointmentsByPatientId(
            @PathVariable UUID patientId,
            @RequestHeader(AppConstants.HEADER_USER_ID) UUID userId,
            @RequestHeader(AppConstants.HEADER_USER_ROLE) String role) {
        List<AppointmentResponse> responses =
                appointmentService.getAppointmentsByPatientId(patientId, userId, role);
        return ResponseEntity.ok(ApiResponse.success(responses));
    }

    @GetMapping("/department/{department}")
    @Operation(summary = "Lịch khám theo chuyên khoa + ngày")
    public ResponseEntity<ApiResponse<List<AppointmentResponse>>> getAppointmentsByDepartment(
            @PathVariable String department,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        List<AppointmentResponse> responses =
                appointmentService.getAppointmentsByDepartmentAndDate(department, date);
        return ResponseEntity.ok(ApiResponse.success(responses));
    }

    @PutMapping("/{id}/status")
    @Operation(summary = "Cập nhật trạng thái lịch khám")
    public ResponseEntity<ApiResponse<AppointmentResponse>> updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateAppointmentStatusRequest request) {
        AppointmentResponse response = appointmentService.updateAppointmentStatus(id, request);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật trạng thái thành công", response));
    }

    @PutMapping("/{id}/cancel")
    @Operation(summary = "Hủy lịch khám")
    public ResponseEntity<ApiResponse<AppointmentResponse>> cancelAppointment(
            @PathVariable UUID id,
            @RequestHeader(AppConstants.HEADER_USER_ID) UUID userId,
            @RequestHeader(AppConstants.HEADER_USER_ROLE) String role,
            @RequestHeader(value = AppConstants.HEADER_CORRELATION_ID, required = false) String correlationId) {
        AppointmentResponse response = appointmentService.cancelAppointment(id, userId, role, correlationId);
        return ResponseEntity.ok(ApiResponse.success("Đã hủy lịch khám", response));
    }

    @GetMapping("/departments")
    @Operation(summary = "Danh sách chuyên khoa")
    public ResponseEntity<ApiResponse<List<Map<String, String>>>> getDepartments() {
        List<Map<String, String>> departments = Arrays.stream(Department.values())
                .map(d -> Map.of(
                        "code", d.name(),
                        "name", d.getDisplayName(),
                        "roomId", d.getRoomId(),
                        "roomName", d.getRoomDisplayName()))
                .toList();
        return ResponseEntity.ok(ApiResponse.success(departments));
    }

    @GetMapping("/time-slots")
    @Operation(summary = "Danh sách ca khám có sẵn")
    public ResponseEntity<ApiResponse<List<String>>> getTimeSlots() {
        // Các ca khám mặc định (30 phút/ca, sáng 7:30-11:30, chiều 13:30-16:30)
        List<String> slots = List.of(
                "07:30-08:00", "08:00-08:30", "08:30-09:00", "09:00-09:30",
                "09:30-10:00", "10:00-10:30", "10:30-11:00", "11:00-11:30",
                "13:30-14:00", "14:00-14:30", "14:30-15:00", "15:00-15:30",
                "15:30-16:00", "16:00-16:30"
        );
        return ResponseEntity.ok(ApiResponse.success(slots));
    }
}
