package com.careflow.consultation.service;

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
     * Hoàn tất phiên khám — chuyển status sang COMPLETED.
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

        // TODO: Đồng bộ dữ liệu sang EMR Service khi EMR sẵn sàng
        // TODO: Publish event cho Analytics Service (thời gian khám + ICD-10)

        return consultationMapper.toResponse(saved);
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
