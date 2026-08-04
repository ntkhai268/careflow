package com.careflow.consultation.service;

import com.careflow.common.constants.AppConstants;
import com.careflow.common.dto.ApiResponse;
import com.careflow.common.exception.BusinessException;
import com.careflow.common.exception.ResourceNotFoundException;
import com.careflow.consultation.client.AppointmentClient;
import com.careflow.consultation.client.dto.AppointmentResponse;
import com.careflow.consultation.client.dto.UpdateAppointmentStatusRequest;
import com.careflow.consultation.dto.request.CreateConsultationRequest;
import com.careflow.consultation.dto.request.UpdateConsultationRequest;
import com.careflow.consultation.dto.request.UpdateConsultationStatusRequest;
import com.careflow.consultation.dto.response.ConsultationResponse;
import com.careflow.consultation.mapper.ConsultationMapper;
import com.careflow.consultation.model.Consultation;
import com.careflow.consultation.model.ConsultationStatus;
import com.careflow.consultation.repository.ConsultationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ConsultationService {

    private final ConsultationRepository consultationRepository;
    private final ConsultationMapper consultationMapper;
    private final RabbitTemplate rabbitTemplate;
    private final AppointmentClient appointmentClient;

    /**
     * Tạo phiên khám mới — status mặc định là IN_PROGRESS.
     * Tích hợp Feign Client để validate appointment và cập nhật trạng thái appointment sang IN_PROGRESS.
     */
    @Transactional
    public ConsultationResponse createConsultation(CreateConsultationRequest request) {
        UUID appointmentId = request.getAppointmentId();

        // 0. Kiểm tra bác sĩ có phiên khám nào chưa hoàn tất (IN_PROGRESS) hay không
        List<Consultation> activeConsultations = consultationRepository.findByDoctorIdAndStatus(
                request.getDoctorId(), ConsultationStatus.IN_PROGRESS);
        if (!activeConsultations.isEmpty()) {
            Consultation existing = activeConsultations.get(0);
            // If the active consultation is for the SAME patient or SAME appointment, return existing active consultation
            if (existing.getPatientId().equals(request.getPatientId()) ||
               (existing.getAppointmentId() != null && existing.getAppointmentId().equals(request.getAppointmentId()))) {
                log.info("Re-entering existing active consultation {} for patient {}", existing.getId(), request.getPatientId());
                return consultationMapper.toResponse(existing);
            }
            throw new BusinessException(400, "Bác sĩ hiện tại đang có một ca khám chưa hoàn tất. Vui lòng hoàn thành lượt khám hiện tại trước khi gọi bệnh nhân khác.");
        }

        // 1. Validate Appointment via Feign Client (if appointment exists in appointment-service)
        try {
            ApiResponse<AppointmentResponse> apptRes = appointmentClient.getAppointmentById(appointmentId);
            if (apptRes != null && apptRes.getData() != null) {
                AppointmentResponse appointment = apptRes.getData();
                String currentStatus = appointment.getStatus();

                if (!"CONFIRMED".equalsIgnoreCase(currentStatus) && !"CHECKED_IN".equalsIgnoreCase(currentStatus) && !"IN_PROGRESS".equalsIgnoreCase(currentStatus)) {
                    throw new BusinessException(400, String.format(
                            "Lịch khám không ở trạng thái hợp lệ để bắt đầu khám (hiện tại: %s, yêu cầu: CONFIRMED hoặc CHECKED_IN)",
                            appointment.getStatusDisplayName() != null ? appointment.getStatusDisplayName() : currentStatus));
                }

                // 2. Cập nhật trạng thái Appointment sang IN_PROGRESS (nếu chưa phải IN_PROGRESS)
                if (!"IN_PROGRESS".equalsIgnoreCase(currentStatus)) {
                    appointmentClient.updateAppointmentStatus(
                            appointmentId,
                            UpdateAppointmentStatusRequest.builder().status("IN_PROGRESS").build()
                    );
                }
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Không tìm thấy hoặc không thể kết nối tới Appointment Service cho appointmentId {}, tiếp tục khởi tạo ca khám với thông tin Queue...", appointmentId);
        }

        // 3. Tạo Consultation Entity
        Consultation consultation = Consultation.builder()
                .appointmentId(appointmentId)
                .patientId(request.getPatientId())
                .doctorId(request.getDoctorId())
                .status(ConsultationStatus.IN_PROGRESS)
                .startedAt(LocalDateTime.now())
                .build();

        Consultation saved = consultationRepository.save(consultation);
        log.info("Created consultation {} for patient {} by doctor {}, appointment {}",
                saved.getId(), saved.getPatientId(), saved.getDoctorId(), saved.getAppointmentId());

        return consultationMapper.toResponse(saved);
    }

    /**
     * Lấy chi tiết phiên khám theo ID.
     */
    public ConsultationResponse getConsultation(UUID id) {
        Consultation consultation = findConsultationOrThrow(id);
        return consultationMapper.toResponse(consultation);
    }

    /**
     * Lấy trạng thái phiên khám dưới dạng String.
     */
    public String getConsultationStatus(UUID id) {
        Consultation consultation = findConsultationOrThrow(id);
        return consultation.getStatus().name();
    }

    /**
     * Cập nhật thông tin phiên khám (sinh hiệu, triệu chứng, chẩn đoán).
     * Cho phép cập nhật nếu chưa bị hủy hoặc hoàn tất.
     */
    @Transactional
    public ConsultationResponse updateConsultation(UUID id, UpdateConsultationRequest request) {
        Consultation consultation = findConsultationOrThrow(id);

        if (consultation.getStatus() == ConsultationStatus.COMPLETED || consultation.getStatus() == ConsultationStatus.CANCELLED) {
            throw new BusinessException(400, "Cannot update consultation with status: " + consultation.getStatus());
        }

        consultationMapper.updateEntityFromRequest(request, consultation);
        Consultation saved = consultationRepository.save(consultation);

        log.info("Updated consultation {}", saved.getId());
        return consultationMapper.toResponse(saved);
    }

    /**
     * Cập nhật trạng thái ca khám (State Machine transition).
     */
    @Transactional
    public ConsultationResponse updateStatus(UUID id, UpdateConsultationStatusRequest request) {
        Consultation consultation = findConsultationOrThrow(id);
        ConsultationStatus newStatus = request.getStatus();

        if (consultation.getStatus() == ConsultationStatus.COMPLETED || consultation.getStatus() == ConsultationStatus.CANCELLED) {
            throw new BusinessException(400, "Cannot change status of a " + consultation.getStatus() + " consultation");
        }

        consultation.setStatus(newStatus);
        if (newStatus == ConsultationStatus.COMPLETED) {
            consultation.setCompletedAt(LocalDateTime.now());
        }

        Consultation saved = consultationRepository.save(consultation);
        log.info("Updated consultation {} status to {}", saved.getId(), newStatus);

        ConsultationResponse response = consultationMapper.toResponse(saved);

        if (newStatus == ConsultationStatus.COMPLETED) {
            publishCompletedEvent(saved.getId(), response);
        }

        return response;
    }

    /**
     * Hoàn tất phiên khám — chuyển status sang COMPLETED và publish event RabbitMQ.
     */
    @Transactional
    public ConsultationResponse completeConsultation(UUID id) {
        Consultation consultation = findConsultationOrThrow(id);

        if (consultation.getStatus() == ConsultationStatus.COMPLETED || consultation.getStatus() == ConsultationStatus.CANCELLED) {
            throw new BusinessException(400, "Cannot complete consultation with status: " + consultation.getStatus());
        }

        consultation.setStatus(ConsultationStatus.COMPLETED);
        consultation.setCompletedAt(LocalDateTime.now());
        Consultation saved = consultationRepository.save(consultation);

        // Update appointment status to COMPLETED if appointmentId exists
        if (saved.getAppointmentId() != null) {
            try {
                appointmentClient.updateAppointmentStatus(
                        saved.getAppointmentId(),
                        UpdateAppointmentStatusRequest.builder().status("COMPLETED").build()
                );
            } catch (Exception e) {
                log.warn("Failed to update appointment status to COMPLETED for appointment {}: {}",
                        saved.getAppointmentId(), e.getMessage());
            }
        }

        log.info("Completed consultation {} — started: {}, completed: {}",
                saved.getId(), saved.getStartedAt(), saved.getCompletedAt());

        ConsultationResponse response = consultationMapper.toResponse(saved);
        publishCompletedEvent(saved.getId(), response);

        return response;
    }

    private void publishCompletedEvent(UUID consultationId, ConsultationResponse response) {
        try {
            rabbitTemplate.convertAndSend(
                AppConstants.EXCHANGE_CONSULTATION,
                AppConstants.RK_CONSULTATION_COMPLETED,
                response
            );
            log.info("Published consultation.completed event for ID {}", consultationId);
        } catch (Exception e) {
            log.warn("Failed to publish consultation.completed event for ID {}: {}", consultationId, e.getMessage());
        }
    }

    /**
     * Lịch sử khám của bệnh nhân.
     */
    public List<ConsultationResponse> getConsultationsByPatient(UUID patientId) {
        return consultationRepository.findByPatientIdOrderByCreatedAtDesc(patientId)
                .stream()
                .map(consultationMapper::toResponse)
                .toList();
    }

    /**
     * Danh sách phiên khám của bác sỹ.
     */
    public List<ConsultationResponse> getConsultationsByDoctor(UUID doctorId) {
        return consultationRepository.findByDoctorIdOrderByCreatedAtDesc(doctorId)
                .stream()
                .map(consultationMapper::toResponse)
                .toList();
    }

    /**
     * Danh sách phiên khám hôm nay của bác sỹ.
     */
    public List<ConsultationResponse> getTodayConsultationsByDoctor(UUID doctorId) {
        java.time.Instant startOfDay = LocalDate.now().atStartOfDay(java.time.ZoneId.systemDefault()).toInstant();
        java.time.Instant endOfDay = LocalDate.now().atTime(LocalTime.MAX).atZone(java.time.ZoneId.systemDefault()).toInstant();

        return consultationRepository.findByDoctorIdAndCreatedAtBetweenOrderByCreatedAtDesc(
                        doctorId, startOfDay, endOfDay)
                .stream()
                .map(consultationMapper::toResponse)
                .toList();
    }

    /**
     * Tra cứu phiên khám theo Appointment ID.
     */
    public List<ConsultationResponse> getConsultationsByAppointment(UUID appointmentId) {
        return consultationRepository.findByAppointmentId(appointmentId)
                .stream()
                .map(consultationMapper::toResponse)
                .toList();
    }

    private Consultation findConsultationOrThrow(UUID id) {
        return consultationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Consultation", "id", id));
    }
}
