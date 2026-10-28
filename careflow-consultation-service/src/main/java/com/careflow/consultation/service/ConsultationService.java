package com.careflow.consultation.service;

import com.careflow.common.constants.AppConstants;
import com.careflow.common.dto.ApiResponse;
import com.careflow.common.exception.BusinessException;
import com.careflow.common.exception.ResourceNotFoundException;
import com.careflow.consultation.client.AppointmentClient;
import com.careflow.consultation.client.ConsultationQueueClient;
import feign.FeignException;
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
    @org.springframework.beans.factory.annotation.Autowired
    private ConsultationEventOutbox eventOutbox;
    @org.springframework.beans.factory.annotation.Autowired
    private ClinicalCompletionGuard completionGuard;

    @org.springframework.beans.factory.annotation.Autowired
    private com.careflow.common.security.PatientOwnershipClient currentOwners;

    private final ConsultationRepository consultationRepository;
    private final ConsultationMapper consultationMapper;
    private final RabbitTemplate rabbitTemplate;
    private final AppointmentClient appointmentClient;
    private final ConsultationQueueClient queueClient;
    private final com.careflow.consultation.client.DirectoryClient directoryClient;
    private final com.careflow.consultation.handover.DoctorWorkloadLock workload;
    private final com.careflow.consultation.client.ConsultationLabClient labs;

    /**
     * Tạo phiên khám mới — status mặc định là IN_PROGRESS.
     * Tích hợp Feign Client để validate appointment và cập nhật trạng thái
     * appointment sang IN_PROGRESS.
     */
    @Transactional
    public ConsultationResponse createConsultation(CreateConsultationRequest request) {
        UUID appointmentId = request.getAppointmentId();
        if (request.getAppointmentId() == null && request.getQueueEntryId() == null)
            throw new BusinessException(400, "Cần mã phiếu hàng đợi cho lượt không hẹn trước.");
        workload.acquire(request.getDoctorId());
        if (request.getQueueEntryId() != null) {
            workload.acquireVisit(request.getQueueEntryId());
            var previous = consultationRepository.findByQueueEntryId(request.getQueueEntryId());
            if (previous.isPresent()) {
                var existing = previous.get();
                if (!request.getDoctorId().equals(existing.currentDoctorId())
                        || !request.getPatientId().equals(existing.getPatientId())
                        || !java.util.Objects.equals(request.getAppointmentId(), existing.getAppointmentId()))
                    throw new BusinessException(403, "Phiếu đã được gắn với một phiên khám khác.");
                return consultationMapper.toResponse(existing);
            }
        }
        String patientName = requireStartedQueueContext(request);

        // 0. Kiểm tra bác sĩ có phiên khám nào chưa hoàn tất (IN_PROGRESS) hay không
        List<Consultation> activeConsultations = consultationRepository.findCurrentByDoctorAndStatus(
                request.getDoctorId(), ConsultationStatus.IN_PROGRESS);
        if (!activeConsultations.isEmpty()) {
            Consultation existing = activeConsultations.get(0);
            // If the active consultation is for the SAME patient or SAME appointment,
            // return existing active consultation
            if ((existing.getQueueEntryId() != null && existing.getQueueEntryId().equals(request.getQueueEntryId())) ||
                    (existing.getAppointmentId() != null
                            && existing.getAppointmentId().equals(request.getAppointmentId()))) {
                log.info("Re-entering existing active consultation {} for patient {}", existing.getId(),
                        request.getPatientId());
                return consultationMapper.toResponse(existing);
            }
            throw new BusinessException(400,
                    "Bác sĩ hiện tại đang có một ca khám chưa hoàn tất. Vui lòng hoàn thành lượt khám hiện tại trước khi gọi bệnh nhân khác.");
        }

        // 2.5 Snapshot Doctor Profile từ Hospital Directory Service (Bảo đảm tính pháp
        // lý y tế)
        String doctorName = null;
        String departmentCode = null;
        String departmentName = null;
        try {
            var docRes = directoryClient.getDoctorByUserId(request.getDoctorId());
            if (docRes != null && docRes.getData() != null) {
                var docProfile = docRes.getData();
                doctorName = docProfile.getFullName();
                departmentCode = docProfile.getDepartmentCode();
                departmentName = docProfile.getDepartmentName();
                log.info("Successfully fetched doctor profile snapshot for userId {}: {}", request.getDoctorId(),
                        doctorName);
            }
        } catch (Exception e) {
            log.warn(
                    "Không thể kết nối Hospital Directory Service để snapshot bác sĩ {}, tiếp tục với snapshot rỗng...",
                    request.getDoctorId());
        }

        // 3. Tạo Consultation Entity
        Consultation consultation = Consultation.builder()
                .appointmentId(appointmentId)
                .queueEntryId(request.getQueueEntryId())
                .patientId(request.getPatientId())
                .patientName(patientName)
                .doctorId(request.getDoctorId())
                .doctorName(doctorName)
                .departmentCode(departmentCode)
                .departmentName(departmentName)
                .status(ConsultationStatus.IN_PROGRESS)
                .startedAt(LocalDateTime.now())
                .build();

        Consultation saved = consultationRepository.save(consultation);
        log.info("Created consultation {} for patient {} by doctor {}, appointment {}",
                saved.getId(), saved.getPatientId(), saved.getDoctorId(), saved.getAppointmentId());

        return consultationMapper.toResponse(saved);
    }

    private String requireStartedQueueContext(CreateConsultationRequest request) {
        String verifiedName = null;
        try {
            if (request.getQueueEntryId() != null) {
                var response = queueClient.clinicalContext(request.getQueueEntryId());
                var context = response == null ? null : response.getData();
                if (context == null)
                    throw new BusinessException(503, "Không thể xác minh lượt khám.");
                if (!request.getQueueEntryId().equals(context.entryId())
                        || !request.getPatientId().equals(context.patientId())
                        || !java.util.Objects.equals(request.getAppointmentId(), context.appointmentId()))
                    throw new BusinessException(403, "Phiếu không khớp phiên khám.");
                if (!"INITIAL".equals(context.phase())
                        || !java.util.Set.of("CALLED", "IN_PROGRESS").contains(context.status()))
                    throw new BusinessException(409, "Cần gọi bệnh nhân trước khi khám.");
                verifiedName = context.patientName();
            } else {
                var response = queueClient.ticket(request.getAppointmentId());
                var ticket = response == null ? null : response.getData();
                if (ticket == null)
                    throw new BusinessException(503, "Unable to verify queue context");
                if (!request.getAppointmentId().equals(ticket.appointmentId())
                        || !request.getPatientId().equals(ticket.patientId()))
                    throw new BusinessException(403, "Queue entry does not match the consultation");
                if (!java.util.Set.of("CALLED", "IN_PROGRESS").contains(ticket.status()))
                    throw new BusinessException(409, "Patient must be called before starting consultation");
                request.setQueueEntryId(ticket.ticketId());
                workload.acquireVisit(ticket.ticketId());
            }
        } catch (FeignException error) {
            throw new BusinessException(error.status() == 401 || error.status() == 403 ? 403 : 503,
                    "Không thể xác minh phân công/lượt khám.");
        }
        if (request.getAppointmentId() == null) {
            if (verifiedName == null || verifiedName.isBlank())
                throw new BusinessException(409, "Phiếu tiếp nhận chưa có thông tin bệnh nhân đã xác minh.");
            return verifiedName;
        }
        try {
            var response = appointmentClient.getAppointmentById(request.getAppointmentId(),
                    request.getDoctorId(), AppConstants.ROLE_DOCTOR);
            var appointment = response == null ? null : response.getData();
            if (appointment == null)
                throw new BusinessException(503, "Unable to verify appointment");
            if (!request.getPatientId().equals(appointment.getPatientId())
                    || !request.getDoctorId().equals(appointment.getDoctorId())) {
                throw new BusinessException(403, "Doctor or patient does not match the appointment");
            }
            if (!java.util.Set.of("CONFIRMED", "CHECKED_IN", "IN_PROGRESS").contains(appointment.getStatus())) {
                throw new BusinessException(409, "Appointment cannot start a consultation");
            }
            return appointment.getPatientName();
        } catch (FeignException error) {
            if (error.status() == 401 || error.status() == 403) {
                throw new BusinessException(403, "Doctor is not assigned to this appointment");
            }
            throw new BusinessException(503, "Unable to verify appointment");
        }
    }

    /**
     * Lấy chi tiết phiên khám theo ID.
     */
    @Transactional
    public ConsultationResponse createConsultation(CreateConsultationRequest request,
            UUID actorUserId, String actorRole) {
        requireDoctor(actorUserId, actorRole);
        if (request.getDoctorId() != null && !actorUserId.equals(request.getDoctorId())) {
            throw new BusinessException(403, "Doctor identity must come from the trusted gateway header");
        }
        request.setDoctorId(actorUserId);
        return createConsultation(request);
    }

    @Transactional
    public ConsultationResponse updateClinicalData(UUID id, UpdateConsultationRequest request,
            UUID actorUserId, String actorRole) {
        Consultation consultation = findConsultationOrThrow(id);
        requireAssignedDoctor(consultation, actorUserId, actorRole);
        return updateConsultation(id, request);
    }

    @Transactional
    public ConsultationResponse waitForResults(UUID id, UUID actorUserId, String actorRole) {
        Consultation consultation = findConsultationOrThrow(id);
        requireAssignedDoctor(consultation, actorUserId, actorRole);
        if (consultation.getStatus() == ConsultationStatus.AWAITING_CLS) {
            return consultationMapper.toResponse(consultation);
        }
        if (consultation.getStatus() != ConsultationStatus.IN_PROGRESS) {
            throw new BusinessException(409, "Consultation is not in progress");
        }
        if (labOrders(consultation).isEmpty())
            throw new BusinessException(409, "Chưa có chỉ định xét nghiệm để chuyển sang chờ kết quả.");
        consultation.setStatus(ConsultationStatus.AWAITING_CLS);
        Consultation saved = consultationRepository.save(consultation);
        return consultationMapper.toResponse(saved);
    }

    @Transactional
    public ConsultationResponse markWaitingForReview(UUID id, UUID patientId) {
        Consultation consultation = findConsultationOrThrow(id);
        if (patientId == null || !consultation.getPatientId().equals(patientId)) {
            throw new BusinessException(403, "Patient identity does not match this consultation");
        }
        if (consultation.getStatus() != ConsultationStatus.AWAITING_CLS) {
            return consultationMapper.toResponse(consultation);
        }
        var orders = labOrders(consultation);
        if (!orders.isEmpty() && orders.stream().allMatch(
                order -> java.util.Set.of("RESULT_AVAILABLE", "REVIEWED", "CANCELLED").contains(order.status()))) {
            consultation.setStatus(ConsultationStatus.AWAITING_REVIEW);
            consultation = consultationRepository.save(consultation);
            log.info("Consultation {} transitioned from AWAITING_CLS to AWAITING_REVIEW", id);
        }
        return consultationMapper.toResponse(consultation);
    }

    @Transactional
    public ConsultationResponse resume(UUID id, UUID actorUserId, String actorRole) {
        Consultation consultation = findConsultationOrThrow(id);
        requireAssignedDoctor(consultation, actorUserId, actorRole);
        if (consultation.getStatus() == ConsultationStatus.IN_PROGRESS) {
            return consultationMapper.toResponse(consultation);
        }
        if (consultation.getStatus() != ConsultationStatus.AWAITING_REVIEW
                && consultation.getStatus() != ConsultationStatus.AWAITING_CLS) {
            throw new BusinessException(409, "Consultation is not waiting for review");
        }
        var labOrders = labOrders(consultation);
        if (labOrders.isEmpty() || labOrders.stream().anyMatch(
                order -> !java.util.Set.of("RESULT_AVAILABLE", "REVIEWED", "CANCELLED").contains(order.status()))) {
            throw new BusinessException(409, "Required laboratory results are still pending");
        }
        workload.acquire(actorUserId);
        if (!consultationRepository.findCurrentByDoctorAndStatus(actorUserId, ConsultationStatus.IN_PROGRESS)
                .isEmpty()) {
            throw new BusinessException(409, "Bác sĩ đang có ca khám khác chưa hoàn tất");
        }
        consultation.setStatus(ConsultationStatus.IN_PROGRESS);
        return consultationMapper.toResponse(consultationRepository.save(consultation));
    }

    @Transactional
    public ConsultationResponse completeConsultation(UUID id, UUID actorUserId, String actorRole) {
        Consultation consultation = findConsultationOrThrow(id);
        requireAssignedDoctor(consultation, actorUserId, actorRole);
        if (consultation.getStatus() == ConsultationStatus.COMPLETED) {
            return consultationMapper.toResponse(consultation);
        }
        if (consultation.getStatus() == ConsultationStatus.CANCELLED) {
            throw new BusinessException(400, "Cannot complete consultation with status: " + consultation.getStatus());
        }
        if (completionGuard != null) {
            completionGuard.requireReady(consultation);
        }
        if (consultation.getStatus() == ConsultationStatus.AWAITING_CLS
                || consultation.getStatus() == ConsultationStatus.AWAITING_REVIEW) {
            throw new BusinessException(409, "Required laboratory review is still pending");
        }
        if (labOrders(consultation).stream()
                .anyMatch(order -> !java.util.Set.of("REVIEWED", "CANCELLED").contains(order.status()))) {
            throw new BusinessException(409, "Laboratory results must be reviewed before completion");
        }

        consultation.setStatus(ConsultationStatus.COMPLETED);
        consultation.setCompletedAt(LocalDateTime.now());
        Consultation saved = consultationRepository.save(consultation);

        log.info("Completed consultation {} — started: {}, completed: {}",
                saved.getId(), saved.getStartedAt(), saved.getCompletedAt());

        ConsultationResponse response = consultationMapper.toResponse(saved);
        publishCompletedEvent(saved.getId(), response);

        return response;
    }

    private List<com.careflow.consultation.client.ConsultationLabClient.Order> labOrders(Consultation consultation) {
        try {
            var response = labs.orders(consultation.getId());
            var orders = response == null ? null : response.getData();
            if (orders == null || orders.stream().anyMatch(order -> order == null || order.status() == null
                    || !consultation.getId().equals(order.consultationId())
                    || !consultation.getPatientId().equals(order.patientId()))) {
                throw new BusinessException(503, "Unable to verify laboratory results");
            }
            return orders;
        } catch (FeignException exception) {
            throw new BusinessException(503, "Unable to verify laboratory results");
        }
    }

    private void requireDoctor(UUID actorUserId, String actorRole) {
        if (actorUserId == null || !AppConstants.ROLE_DOCTOR.equalsIgnoreCase(actorRole)) {
            throw new BusinessException(403, "Assigned doctor access is required");
        }
    }

    @Transactional
    public ConsultationResponse updateStatus(UUID id, UpdateConsultationStatusRequest request,
            UUID actorUserId, String actorRole) {
        Consultation consultation = findConsultationOrThrow(id);
        requireAssignedDoctor(consultation, actorUserId, actorRole);
        return switch (request.getStatus()) {
            case IN_PROGRESS -> resume(id, actorUserId, actorRole);
            case AWAITING_CLS -> waitForResults(id, actorUserId, actorRole);
            case COMPLETED -> completeConsultation(id, actorUserId, actorRole);
            case CANCELLED -> updateStatus(id, request);
            default -> throw new BusinessException(409, "Status is managed by the clinical workflow");
        };
    }

    private void requireAssignedDoctor(Consultation consultation, UUID actorUserId, String actorRole) {
        requireDoctor(actorUserId, actorRole);
        if (!actorUserId.equals(consultation.currentDoctorId())) {
            throw new BusinessException(403, "Doctor is not assigned to this consultation");
        }
    }

    public ConsultationResponse getConsultation(UUID id, UUID actorUserId, String actorRole) {
        Consultation consultation = findConsultationOrThrow(id);
        requireReadAccess(consultation, actorUserId, actorRole);
        return consultationMapper.toResponse(consultation);
    }

    public ConsultationResponse getConsultation(UUID id) {
        return getConsultation(id, null, null);
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

        if (consultation.getStatus() == ConsultationStatus.COMPLETED
                || consultation.getStatus() == ConsultationStatus.CANCELLED) {
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

        if (consultation.getStatus() == ConsultationStatus.COMPLETED
                || consultation.getStatus() == ConsultationStatus.CANCELLED) {
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

    private void publishCompletedEvent(UUID consultationId, ConsultationResponse response) {
        if (eventOutbox != null) {
            eventOutbox.append(consultationId, "ConsultationCompleted", AppConstants.RK_CONSULTATION_COMPLETED,
                    response);
            return;
        }
        try {
            rabbitTemplate.convertAndSend(
                    AppConstants.EXCHANGE_CONSULTATION,
                    AppConstants.RK_CONSULTATION_COMPLETED,
                    response);
            log.info("Published consultation.completed event for ID {}", consultationId);
        } catch (Exception e) {
            log.warn("Failed to publish consultation.completed event for ID {}: {}", consultationId, e.getMessage());
        }
    }

    /**
     * Lịch sử khám của bệnh nhân.
     */
    public List<ConsultationResponse> getConsultationsByPatient(
            UUID patientId, UUID actorUserId, String actorRole) {
        if (AppConstants.ROLE_PATIENT.equalsIgnoreCase(actorRole)
                && actorUserId == null) {
            throw new BusinessException(403, "Patient identity is required");
        }
        return consultationRepository.findByPatientIdOrderByCreatedAtDesc(patientId)
                .stream()
                .peek(value -> requireReadAccess(value, actorUserId, actorRole))
                .map(consultationMapper::toResponse)
                .toList();
    }

    public List<ConsultationResponse> getConsultationsByPatient(UUID patientId) {
        return getConsultationsByPatient(patientId, null, null);
    }

    /**
     * Danh sách phiên khám của bác sỹ.
     */
    public List<ConsultationResponse> getConsultationsByDoctor(
            UUID doctorId, UUID actorUserId, String actorRole) {
        requireClinicalListAccess(doctorId, actorUserId, actorRole);
        return consultationRepository.findCurrentByDoctor(doctorId)
                .stream()
                .map(consultationMapper::toResponse)
                .toList();
    }

    public List<ConsultationResponse> getConsultationsByDoctor(UUID doctorId) {
        return getConsultationsByDoctor(doctorId, null, null);
    }

    /**
     * Danh sách phiên khám hôm nay của bác sỹ.
     */
    public List<ConsultationResponse> getTodayConsultationsByDoctor(
            UUID doctorId, UUID actorUserId, String actorRole) {
        requireClinicalListAccess(doctorId, actorUserId, actorRole);
        java.time.Instant startOfDay = LocalDate.now().atStartOfDay(java.time.ZoneId.systemDefault()).toInstant();
        java.time.Instant endOfDay = LocalDate.now().atTime(LocalTime.MAX).atZone(java.time.ZoneId.systemDefault())
                .toInstant();

        return consultationRepository.findCurrentByDoctorAndDate(
                doctorId, startOfDay, endOfDay)
                .stream()
                .map(consultationMapper::toResponse)
                .toList();
    }

    public List<ConsultationResponse> getTodayConsultationsByDoctor(UUID doctorId) {
        return getTodayConsultationsByDoctor(doctorId, null, null);
    }

    /**
     * Tra cứu phiên khám theo Appointment ID.
     */
    public List<ConsultationResponse> getConsultationsByAppointment(
            UUID appointmentId, UUID actorUserId, String actorRole) {
        return consultationRepository.findByAppointmentId(appointmentId)
                .stream()
                .peek(value -> requireReadAccess(value, actorUserId, actorRole))
                .map(consultationMapper::toResponse)
                .toList();
    }

    public List<ConsultationResponse> getConsultationsByAppointment(UUID appointmentId) {
        return getConsultationsByAppointment(appointmentId, null, null);
    }

    private void requireClinicalListAccess(UUID doctorId, UUID actorUserId, String actorRole) {
        if (!AppConstants.ROLE_DOCTOR.equalsIgnoreCase(actorRole)
                || actorUserId == null || !actorUserId.equals(doctorId)) {
            throw new BusinessException(403, "Clinical consultation access is not allowed");
        }
    }

    private void requireReadAccess(Consultation consultation, UUID actorUserId, String actorRole) {
        if (actorUserId == null || actorRole == null) {
            throw new BusinessException(403, "Authenticated consultation access is required");
        }
        if (AppConstants.ROLE_DOCTOR.equalsIgnoreCase(actorRole)) {
            if (!actorUserId.equals(consultation.currentDoctorId())) {
                throw new BusinessException(403, "Doctor is not assigned to this consultation");
            }
            return;
        }
        if (AppConstants.ROLE_PATIENT.equalsIgnoreCase(actorRole) && currentOwners != null) {
            currentOwners.require(consultation.getPatientId(), actorUserId);
            return;
        }
        if (!AppConstants.ROLE_PATIENT.equalsIgnoreCase(actorRole)
                || consultation.getAppointmentId() == null) {
            throw new BusinessException(403, "Patient consultation access is not allowed");
        }
        try {
            ApiResponse<AppointmentResponse> appointment = appointmentClient.getAppointmentById(
                    consultation.getAppointmentId(), actorUserId, actorRole);
            if (appointment == null || appointment.getData() == null
                    || !consultation.getPatientId().equals(appointment.getData().getPatientId())) {
                throw new BusinessException(403, "Patient consultation access is not allowed");
            }
        } catch (BusinessException exception) {
            throw exception;
        } catch (FeignException exception) {
            if (exception.status() == 403 || exception.status() == 404) {
                throw new BusinessException(403, "Patient consultation access is not allowed");
            }
            throw new BusinessException(503, "Unable to verify consultation ownership");
        } catch (Exception exception) {
            throw new BusinessException(503, "Unable to verify consultation ownership");
        }
    }

    private Consultation findConsultationOrThrow(UUID id) {
        return consultationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Consultation", "id", id));
    }
}
