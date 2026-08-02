package com.careflow.appointment.service;

import com.careflow.appointment.config.RabbitMQConfig;
import com.careflow.common.constants.AppConstants;
import com.careflow.appointment.dto.request.CreateAppointmentRequest;
import com.careflow.appointment.dto.request.UpdateAppointmentStatusRequest;
import com.careflow.appointment.dto.response.AppointmentResponse;
import com.careflow.appointment.mapper.AppointmentMapper;
import com.careflow.appointment.model.Appointment;
import com.careflow.appointment.model.AppointmentStatus;
import com.careflow.appointment.model.Department;
import com.careflow.appointment.repository.AppointmentRepository;
import com.careflow.common.exception.BusinessException;
import com.careflow.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AppointmentService {
    private static final ZoneId HOSPITAL_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");


    private final AppointmentRepository appointmentRepository;
    private final RabbitTemplate rabbitTemplate;

    /**
     * Đặt lịch khám mới
     */
    @Transactional
    public AppointmentResponse createAppointment(CreateAppointmentRequest request) {
        validateAppointmentTime(request, Clock.system(HOSPITAL_ZONE));

        // Parse department
        Department department;
        try {
            department = Department.valueOf(request.getDepartment());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(400, "Chuyên khoa không hợp lệ: " + request.getDepartment());
        }

        // Kiểm tra trùng lịch (cùng bệnh nhân, cùng ngày, cùng ca — không tính đã hủy)
        if (appointmentRepository.existsByPatientIdAndAppointmentDateAndTimeSlotAndStatusNot(
                request.getPatientId(),
                request.getAppointmentDate(),
                request.getTimeSlot(),
                AppointmentStatus.CANCELLED)) {
            throw new BusinessException(409, "Bệnh nhân đã có lịch khám vào ca này");
        }

        Appointment appointment = Appointment.builder()
                .patientId(request.getPatientId())
                .patientName(request.getPatientName())
                .department(department)
                .appointmentDate(request.getAppointmentDate())
                .timeSlot(request.getTimeSlot())
                .reason(request.getReason())
                // Online bookings are accepted immediately. The patient app
                // can issue the visit ticket without waiting for a manual
                // confirmation step that does not exist in the agreed flow.
                .status(AppointmentStatus.CONFIRMED)
                .build();

        Appointment saved = appointmentRepository.save(appointment);
        log.info("Created appointment {} for patient {} at {} {}",
                saved.getId(), saved.getPatientId(),
                saved.getAppointmentDate(), saved.getTimeSlot());

        // Publish event to RabbitMQ
        publishAppointmentCreatedEvent(saved);

        return AppointmentMapper.toResponse(saved);
    }

    /**
     * Xem chi tiết lịch khám
     */
    @Transactional(readOnly = true)
    public AppointmentResponse getAppointmentById(UUID id) {
        Appointment appointment = findAppointmentOrThrow(id);
        return AppointmentMapper.toResponse(appointment);
    }

    /**
     * Danh sách lịch khám của bệnh nhân
     */
    @Transactional(readOnly = true)
    public List<AppointmentResponse> getAppointmentsByPatientId(UUID patientId) {
        return appointmentRepository.findByPatientIdOrderByAppointmentDateDesc(patientId)
                .stream()
                .map(AppointmentMapper::toResponse)
                .toList();
    }

    /**
     * Lịch khám theo chuyên khoa + ngày (cho bác sỹ/admin)
     */
    @Transactional(readOnly = true)
    public List<AppointmentResponse> getAppointmentsByDepartmentAndDate(
            String departmentStr, LocalDate date) {
        Department department;
        try {
            department = Department.valueOf(departmentStr);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(400, "Chuyên khoa không hợp lệ: " + departmentStr);
        }

        return appointmentRepository
                .findByDepartmentAndAppointmentDateOrderByTimeSlot(department, date)
                .stream()
                .map(AppointmentMapper::toResponse)
                .toList();
    }

    /**
     * Cập nhật trạng thái lịch khám
     */
    @Transactional
    public AppointmentResponse updateAppointmentStatus(UUID id,
                                                        UpdateAppointmentStatusRequest request) {
        Appointment appointment = findAppointmentOrThrow(id);

        AppointmentStatus newStatus;
        try {
            newStatus = AppointmentStatus.valueOf(request.getStatus());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(400, "Trạng thái không hợp lệ: " + request.getStatus());
        }

        // Validate state transition
        AppointmentStatus oldStatus = appointment.getStatus();
        validateStatusTransition(oldStatus, newStatus);

        appointment.setStatus(newStatus);
        if (request.getNotes() != null) {
            appointment.setNotes(request.getNotes());
        }

        Appointment updated = appointmentRepository.save(appointment);
        log.info("Updated appointment {} status: {} → {}",
                id, oldStatus, newStatus);

        return AppointmentMapper.toResponse(updated);
    }

    /**
     * Hủy lịch khám
     */
    @Transactional
    public AppointmentResponse cancelAppointment(UUID id) {
        Appointment appointment = findAppointmentOrThrow(id);

        if (appointment.getStatus() == AppointmentStatus.COMPLETED) {
            throw new BusinessException(400, "Không thể hủy lịch khám đã hoàn thành");
        }
        if (appointment.getStatus() == AppointmentStatus.CANCELLED) {
            throw new BusinessException(400, "Lịch khám đã được hủy trước đó");
        }

        appointment.setStatus(AppointmentStatus.CANCELLED);
        Appointment updated = appointmentRepository.save(appointment);
        log.info("Cancelled appointment {}", id);

        // Publish cancel event
        publishAppointmentCancelledEvent(updated);

        return AppointmentMapper.toResponse(updated);
    }

    // ==================== Private helpers ====================

    private Appointment findAppointmentOrThrow(UUID id) {
        return appointmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment", "id", id));
    }

    static void validateAppointmentTime(CreateAppointmentRequest request, Clock clock) {
        LocalDate today = LocalDate.now(clock);
        if (request.getAppointmentDate().isBefore(today)) {
            throw new BusinessException(400, "Ngày khám đã qua");
        }
        if (!request.getAppointmentDate().isEqual(today)) {
            return;
        }

        try {
            String startValue = request.getTimeSlot().split("-", 2)[0].trim();
            LocalTime slotStart = LocalTime.parse(startValue);
            if (!LocalTime.now(clock).isBefore(slotStart)) {
                throw new BusinessException(400, "Ca khám đã qua. Vui lòng chọn ca khác");
            }
        } catch (DateTimeParseException e) {
            throw new BusinessException(400, "Định dạng ca khám không hợp lệ");
        }
    }

    private void validateStatusTransition(AppointmentStatus current, AppointmentStatus next) {
        // Valid transitions:
        // PENDING → CONFIRMED, CANCELLED
        // CONFIRMED → CHECKED_IN, CANCELLED
        // CHECKED_IN → IN_PROGRESS, CANCELLED
        // IN_PROGRESS → COMPLETED
        boolean valid = switch (current) {
            case PENDING -> next == AppointmentStatus.CONFIRMED || next == AppointmentStatus.CANCELLED;
            case CONFIRMED -> next == AppointmentStatus.CHECKED_IN || next == AppointmentStatus.IN_PROGRESS || next == AppointmentStatus.CANCELLED;
            case CHECKED_IN -> next == AppointmentStatus.IN_PROGRESS || next == AppointmentStatus.CANCELLED;
            case IN_PROGRESS -> next == AppointmentStatus.COMPLETED;
            case COMPLETED, CANCELLED -> false;
        };

        if (!valid) {
            throw new BusinessException(400,
                    String.format("Không thể chuyển trạng thái từ %s sang %s",
                            current.getDisplayName(), next.getDisplayName()));
        }
    }

    private void publishAppointmentCreatedEvent(Appointment appointment) {
        try {
            Map<String, Object> event = Map.of(
                    "appointmentId", appointment.getId().toString(),
                    "patientId", appointment.getPatientId().toString(),
                    "patientName", appointment.getPatientName() != null ? appointment.getPatientName() : "",
                    "department", appointment.getDepartment().name(),
                    "appointmentDate", appointment.getAppointmentDate().toString(),
                    "timeSlot", appointment.getTimeSlot(),
                    "createdAt", java.time.Instant.now().toString()
            );

            rabbitTemplate.convertAndSend(
                    AppConstants.EXCHANGE_APPOINTMENT,
                    AppConstants.RK_APPOINTMENT_CREATED,
                    event);

            log.info("Published AppointmentCreated event for {}", appointment.getId());
        } catch (Exception e) {
            // Don't fail the request if RabbitMQ is down
            log.warn("Failed to publish AppointmentCreated event: {}", e.getMessage());
        }
    }

    private void publishAppointmentCancelledEvent(Appointment appointment) {
        try {
            Map<String, Object> event = Map.of(
                    "appointmentId", appointment.getId().toString(),
                    "patientId", appointment.getPatientId().toString(),
                    "department", appointment.getDepartment().name()
            );

            rabbitTemplate.convertAndSend(
                    AppConstants.EXCHANGE_APPOINTMENT,
                    AppConstants.RK_APPOINTMENT_CANCELLED,
                    event);

            log.info("Published AppointmentCancelled event for {}", appointment.getId());
        } catch (Exception e) {
            log.warn("Failed to publish AppointmentCancelled event: {}", e.getMessage());
        }
    }
}
