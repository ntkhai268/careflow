package com.careflow.consultation.service;

import com.careflow.common.constants.AppConstants;
import com.careflow.common.exception.BusinessException;
import com.careflow.common.exception.ResourceNotFoundException;
import com.careflow.consultation.dto.request.CreateConsultationRequest;
import com.careflow.consultation.dto.request.UpdateConsultationRequest;
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

    /**
     * Tạo phiên khám mới — status mặc định là IN_PROGRESS.
     */
    @Transactional
    public ConsultationResponse createConsultation(CreateConsultationRequest request) {
        Consultation consultation = Consultation.builder()
                .appointmentId(request.getAppointmentId())
                .patientId(request.getPatientId())
                .doctorId(request.getDoctorId())
                .status(ConsultationStatus.IN_PROGRESS)
                .startedAt(LocalDateTime.now())
                .build();

        Consultation saved = consultationRepository.save(consultation);
        log.info("Created consultation {} for patient {} by doctor {}",
                saved.getId(), saved.getPatientId(), saved.getDoctorId());

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
     * Cập nhật phiên khám (sinh hiệu, triệu chứng, chẩn đoán).
     * Chỉ cho phép cập nhật khi status = IN_PROGRESS.
     */
    @Transactional
    public ConsultationResponse updateConsultation(UUID id, UpdateConsultationRequest request) {
        Consultation consultation = findConsultationOrThrow(id);

        if (consultation.getStatus() != ConsultationStatus.IN_PROGRESS) {
            throw new BusinessException(400, "Cannot update consultation with status: " + consultation.getStatus());
        }

        consultationMapper.updateEntityFromRequest(request, consultation);
        Consultation saved = consultationRepository.save(consultation);

        log.info("Updated consultation {}", saved.getId());
        return consultationMapper.toResponse(saved);
    }

    /**
     * Hoàn tất phiên khám — chuyển status sang COMPLETED và publish event RabbitMQ.
     */
    @Transactional
    public ConsultationResponse completeConsultation(UUID id) {
        Consultation consultation = findConsultationOrThrow(id);

        if (consultation.getStatus() != ConsultationStatus.IN_PROGRESS) {
            throw new BusinessException(400, "Cannot complete consultation with status: " + consultation.getStatus());
        }

        consultation.setStatus(ConsultationStatus.COMPLETED);
        consultation.setCompletedAt(LocalDateTime.now());
        Consultation saved = consultationRepository.save(consultation);

        log.info("Completed consultation {} — started: {}, completed: {}",
                saved.getId(), saved.getStartedAt(), saved.getCompletedAt());

        ConsultationResponse response = consultationMapper.toResponse(saved);

        // Publish event ConsultationCompleted lên RabbitMQ Broker
        try {
            rabbitTemplate.convertAndSend(
                AppConstants.EXCHANGE_CONSULTATION,
                AppConstants.RK_CONSULTATION_COMPLETED,
                response
            );
            log.info("Published consultation.completed event for ID {}", saved.getId());
        } catch (Exception e) {
            log.warn("Failed to publish consultation.completed event for ID {}: {}", saved.getId(), e.getMessage());
        }

        return response;
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
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        LocalDateTime endOfDay = LocalDate.now().atTime(LocalTime.MAX);

        return consultationRepository.findByDoctorIdAndCreatedAtBetweenOrderByCreatedAtDesc(
                        doctorId, startOfDay, endOfDay)
                .stream()
                .map(consultationMapper::toResponse)
                .toList();
    }

    private Consultation findConsultationOrThrow(UUID id) {
        return consultationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Consultation", "id", id));
    }
}
