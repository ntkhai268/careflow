package com.careflow.prescription.service;

import com.careflow.common.constants.AppConstants;
import com.careflow.common.exception.BusinessException;
import com.careflow.common.exception.ResourceNotFoundException;
import com.careflow.common.event.EventEnvelope;
import com.careflow.prescription.config.AppointmentClient;
import com.careflow.prescription.config.ConsultationClient;
import com.careflow.prescription.config.DirectoryClient;
import com.careflow.prescription.config.QueueExecutionClient;
import com.careflow.prescription.config.PatientIdentityClient;
import com.careflow.prescription.dto.request.AmendPrescriptionRequest;
import com.careflow.prescription.dto.request.CreatePrescriptionRequest;
import com.careflow.prescription.dto.request.PrescriptionItemRequest;
import com.careflow.prescription.dto.response.PrescriptionResponse;
import com.careflow.prescription.mapper.PrescriptionMapper;
import com.careflow.prescription.model.Prescription;
import com.careflow.prescription.model.PrescriptionItem;
import com.careflow.prescription.model.PrescriptionStatus;
import com.careflow.prescription.repository.DrugRepository;
import com.careflow.prescription.repository.PrescriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.time.Instant;

@Service
@RequiredArgsConstructor
@Slf4j
public class PrescriptionService {

    private final PrescriptionRepository prescriptionRepository;
    private final DrugRepository drugRepository;
    private final PrescriptionMapper prescriptionMapper;
    private final ConsultationClient consultationClient;
    private final AppointmentClient appointmentClient;
    private final DirectoryClient directoryClient;
    private final QueueExecutionClient queueExecutionClient;
    private final PatientIdentityClient patientIdentityClient;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    @Transactional
    public PrescriptionResponse createPrescription(CreatePrescriptionRequest request,
                                                    UUID actorUserId, String actorRole) {
        requireDoctor(actorUserId, actorRole);
        if (request.getDoctorId() != null && !actorUserId.equals(request.getDoctorId())) {
            throw new BusinessException(403, "Doctor identity must come from the trusted gateway header");
        }
        request.setDoctorId(actorUserId);
        return createPrescription(request);
    }

    private void requireDoctor(UUID actorUserId, String actorRole) {
        if (actorUserId == null || !AppConstants.ROLE_DOCTOR.equalsIgnoreCase(actorRole)) {
            throw new BusinessException(403, "Assigned doctor access is required");
        }
    }

    private void validateConsultationStatus(UUID consultationId) {
        String status = consultationClient.getConsultationStatus(consultationId);
        if (status != null && (status.equalsIgnoreCase("COMPLETED") || status.equalsIgnoreCase("CANCELLED"))) {
            throw new BusinessException(400, "Không thể thao tác đơn thuốc vì phiên khám đã hoàn tất hoặc bị hủy (Trạng thái: " + status + ")");
        }
    }

    private void validateMedicineItems(CreatePrescriptionRequest request) {
        if (request.getItems() == null) return;
        for (var item : request.getItems()) {
            boolean valid = drugRepository.existsByCode(item.getMedicineCode());
            if (!valid) {
                throw new BusinessException(400, "Thuốc " + item.getMedicineName() + " (Mã: " + item.getMedicineCode() + ") không nằm trong danh mục được phép kê của bệnh viện.");
            }
        }
    }

    /**
     * Tạo đơn thuốc mới với danh sách thuốc kèm theo.
     * Status mặc định: DRAFT — bác sỹ có thể chỉnh sửa trước khi xác nhận.
     */
    private void validateAmendmentMedicineItems(AmendPrescriptionRequest request) {
        if (request.getItems() == null) return;
        for (PrescriptionItemRequest item : request.getItems()) {
            if (!drugRepository.existsByCode(item.getMedicineCode())) {
                throw new BusinessException(400, "Medicine is not in the hospital catalog: "
                        + item.getMedicineCode());
            }
        }
    }

    private void validateCompletedConsultationForAmendment(UUID consultationId) {
        String status = consultationClient.getConsultationStatus(consultationId);
        if (status != null && status.equalsIgnoreCase("CANCELLED")) {
            throw new BusinessException(400, "Cannot amend a prescription for a cancelled consultation");
        }
    }

    @Transactional
    public PrescriptionResponse createPrescription(CreatePrescriptionRequest request) {
        // Validate consultation status
        validateConsultationStatus(request.getConsultationId());

        // Validate medicine codes
        validateMedicineItems(request);

        Prescription prescription = Prescription.builder()
                .consultationId(request.getConsultationId())
                .patientId(request.getPatientId())
                .doctorId(request.getDoctorId())
                .diagnosis(request.getDiagnosis())
                .notes(request.getNotes())
                .followUpDate(request.getFollowUpDate())
                .status(PrescriptionStatus.DRAFT)
                .build();

        // Thêm từng thuốc vào đơn
        request.getItems().forEach(itemRequest -> {
            PrescriptionItem item = prescriptionMapper.toItemEntity(itemRequest);
            prescription.addItem(item);
        });

        Prescription saved = prescriptionRepository.save(prescription);
        log.info("Created prescription {} with {} items for consultation {}",
                saved.getId(), saved.getItems().size(), saved.getConsultationId());

        return prescriptionMapper.toResponse(saved);
    }

    /**
     * Lấy chi tiết đơn thuốc theo ID.
     */
    @Transactional(readOnly = true)
    public PrescriptionResponse getPrescription(UUID id, UUID actorUserId, String actorRole) {
        Prescription prescription = findPrescriptionOrThrow(id);
        requireReadAccess(prescription, actorUserId, actorRole);
        return prescriptionMapper.toResponse(prescription);
    }

    public PrescriptionResponse getPrescription(UUID id) {
        return getPrescription(id, null, null);
    }

    /**
     * Cập nhật đơn thuốc (chỉ khi status = DRAFT).
     * Xóa toàn bộ items cũ và thay bằng items mới từ request.
     */
    @Transactional
    public PrescriptionResponse updatePrescription(UUID id, CreatePrescriptionRequest request) {
        Prescription prescription = findPrescriptionOrThrow(id);

        if (prescription.getStatus() != PrescriptionStatus.DRAFT) {
            throw new BusinessException(400, "Cannot update prescription with status: " + prescription.getStatus());
        }

        // Validate consultation status
        validateConsultationStatus(prescription.getConsultationId());

        // Validate medicine codes
        validateMedicineItems(request);

        // Cập nhật thông tin chung
        prescription.setDiagnosis(request.getDiagnosis());
        prescription.setNotes(request.getNotes());
        prescription.setFollowUpDate(request.getFollowUpDate());

        // Xóa items cũ, thêm items mới
        prescription.clearItems();
        request.getItems().forEach(itemRequest -> {
            PrescriptionItem item = prescriptionMapper.toItemEntity(itemRequest);
            prescription.addItem(item);
        });

        Prescription saved = prescriptionRepository.save(prescription);
        log.info("Updated prescription {} with {} items", saved.getId(), saved.getItems().size());

        return prescriptionMapper.toResponse(saved);
    }

    /**
     * Xác nhận đơn thuốc (DRAFT → CONFIRMED).
     * Sau khi xác nhận, đơn thuốc không thể sửa nữa.
     * Sẽ publish event PrescriptionCreated lên RabbitMQ.
     */
    @Transactional
    public PrescriptionResponse confirmPrescription(UUID id, UUID actorUserId, String actorRole) {
        Prescription prescription = findPrescriptionOrThrow(id);
        requireDoctor(actorUserId, actorRole);
        if (!actorUserId.equals(prescription.getDoctorId())) {
            throw new BusinessException(403, "Doctor is not assigned to this prescription");
        }

        if (prescription.getStatus() != PrescriptionStatus.DRAFT) {
            throw new BusinessException(400, "Cannot confirm prescription with status: " + prescription.getStatus());
        }

        if (prescription.getItems().isEmpty()) {
            throw new BusinessException(400, "Cannot confirm prescription with no medicine items");
        }

        // Validate consultation status
        if (prescription.getReplacesPrescriptionId() != null) {
            validateCompletedConsultationForAmendment(prescription.getConsultationId());
        } else {
            validateConsultationStatus(prescription.getConsultationId());
        }

        prescription.setStatus(PrescriptionStatus.CONFIRMED);
        prescription.setDispensingServicePointId(directoryClient.getActivePharmacyServicePointId());
        prescription.setConfirmedAt(Instant.now());
        Prescription saved = prescriptionRepository.save(prescription);

        log.info("Confirmed prescription {} — publishing event", saved.getId());

        // Publish event PrescriptionCreated lên RabbitMQ
        var response = prescriptionMapper.toResponse(saved);
        Map<String, Object> eventPayload = new LinkedHashMap<>();
        eventPayload.put("prescriptionId", saved.getId());
        eventPayload.put("consultationId", saved.getConsultationId());
        eventPayload.put("patientId", saved.getPatientId());
        eventPayload.put("doctorId", saved.getDoctorId());
        eventPayload.put("dispensingServicePointId", saved.getDispensingServicePointId());
        eventPayload.put("itemCount", saved.getItems().size());
        eventPayload.put("issuedAt", Instant.now());
        var payload = objectMapper.valueToTree(eventPayload);
        EventEnvelope envelope = new EventEnvelope(
                UUID.randomUUID(), "PrescriptionIssued", 1, saved.getId(), 1,
                Instant.now(), "prescription-service", saved.getId().toString(), payload);
        rabbitTemplate.convertAndSend(AppConstants.EXCHANGE_PRESCRIPTION,
                AppConstants.RK_PRESCRIPTION_ISSUED, envelope);

        // Tự động kích hoạt tạo lịch hẹn tái khám nếu có followUpDate
        if (saved.getFollowUpDate() != null) {
            appointmentClient.createFollowUpAppointment(
                    saved.getPatientId(),
                    saved.getDoctorId(),
                    saved.getConsultationId(),
                    saved.getFollowUpDate(),
                    saved.getNotes(),
                    actorUserId
            );
        }

        return response;
    }

    @Transactional
    public PrescriptionResponse cancelPrescription(UUID id, String reason,
                                                   UUID actorUserId, String actorRole) {
        if (actorUserId == null || actorRole == null
                || (!AppConstants.ROLE_DOCTOR.equalsIgnoreCase(actorRole)
                && !AppConstants.ROLE_ADMIN.equalsIgnoreCase(actorRole))) {
            throw new BusinessException(403, "Doctor or ADMIN access is required");
        }

        Prescription prescription = findPrescriptionOrThrow(id);
        if (AppConstants.ROLE_DOCTOR.equalsIgnoreCase(actorRole)
                && !actorUserId.equals(prescription.getDoctorId())) {
            throw new BusinessException(403, "Only the assigned doctor can cancel this prescription");
        }
        if (prescription.getStatus() == PrescriptionStatus.CANCELLED) {
            return prescriptionMapper.toResponse(prescription);
        }
        if (prescription.getStatus() == PrescriptionStatus.CANCELLED_BY_AMENDMENT) {
            throw new BusinessException(409, "Prescription has already been replaced by an amendment");
        }
        if (prescription.getStatus() == PrescriptionStatus.DISPENSED) {
            throw new BusinessException(409, "A dispensed prescription cannot be cancelled");
        }
        if (prescription.getStatus() == PrescriptionStatus.CONFIRMED
                && AppConstants.ROLE_DOCTOR.equalsIgnoreCase(actorRole)) {
            throw new BusinessException(409, "A confirmed prescription must be replaced through an amendment");
        }

        prescription.setStatus(PrescriptionStatus.CANCELLED);
        prescription.setCancellationReason(reason);
        prescription.setCancelledAt(Instant.now());
        prescription.setCancelledByUserId(actorUserId);
        Prescription saved = prescriptionRepository.save(prescription);
        publishCancellation(saved, reason, actorUserId);
        return prescriptionMapper.toResponse(saved);
    }

    @Transactional
    public PrescriptionResponse updatePrescription(UUID id, CreatePrescriptionRequest request,
                                                   UUID actorUserId, String actorRole) {
        requireDoctor(actorUserId, actorRole);
        Prescription prescription = findPrescriptionOrThrow(id);
        if (!actorUserId.equals(prescription.getDoctorId())) {
            throw new BusinessException(403, "Only the assigned doctor can update this prescription");
        }
        request.setDoctorId(actorUserId);
        return updatePrescription(id, request);
    }

    @Transactional
    public PrescriptionResponse amendPrescription(UUID id, AmendPrescriptionRequest request,
                                                  UUID actorUserId, String actorRole) {
        requireDoctor(actorUserId, actorRole);
        Prescription original = findPrescriptionOrThrow(id);
        if (!actorUserId.equals(original.getDoctorId())) {
            throw new BusinessException(403, "Only the assigned doctor can amend this prescription");
        }
        if (original.getStatus() != PrescriptionStatus.CONFIRMED) {
            throw new BusinessException(409, "Only a confirmed prescription can be amended");
        }

        validateAmendmentMedicineItems(request);
        Prescription replacement = Prescription.builder()
                .consultationId(original.getConsultationId())
                .patientId(original.getPatientId())
                .doctorId(original.getDoctorId())
                .diagnosis(request.getDiagnosis())
                .notes(request.getNotes())
                .followUpDate(request.getFollowUpDate())
                .replacesPrescriptionId(original.getId())
                .status(PrescriptionStatus.DRAFT)
                .build();
        request.getItems().forEach(itemRequest ->
                replacement.addItem(prescriptionMapper.toItemEntity(itemRequest)));

        Prescription savedReplacement = prescriptionRepository.save(replacement);
        original.setStatus(PrescriptionStatus.CANCELLED_BY_AMENDMENT);
        original.setCancellationReason("Replaced by prescription " + savedReplacement.getId());
        original.setCancelledAt(Instant.now());
        original.setCancelledByUserId(actorUserId);
        prescriptionRepository.save(original);
        publishCancellation(original, original.getCancellationReason(), actorUserId);

        return prescriptionMapper.toResponse(savedReplacement);
    }

    private void publishCancellation(Prescription prescription, String reason, UUID actorUserId) {
        Map<String, Object> eventPayload = new LinkedHashMap<>();
        eventPayload.put("prescriptionId", prescription.getId());
        eventPayload.put("consultationId", prescription.getConsultationId());
        eventPayload.put("patientId", prescription.getPatientId());
        eventPayload.put("doctorId", prescription.getDoctorId());
        eventPayload.put("replacementPrescriptionId", prescription.getReplacesPrescriptionId());
        eventPayload.put("reason", reason);
        eventPayload.put("cancelledByUserId", actorUserId);
        eventPayload.put("cancelledAt", prescription.getCancelledAt());
        EventEnvelope envelope = new EventEnvelope(
                UUID.randomUUID(), "PrescriptionCancelled", 1, prescription.getId(), 1,
                prescription.getCancelledAt() == null ? Instant.now() : prescription.getCancelledAt(),
                "prescription-service", prescription.getId().toString(),
                objectMapper.valueToTree(eventPayload));
        rabbitTemplate.convertAndSend(AppConstants.EXCHANGE_PRESCRIPTION,
                AppConstants.RK_PRESCRIPTION_CANCELLED, envelope);
    }

    @Transactional
    public PrescriptionResponse dispensePrescription(UUID id, UUID actorUserId, String actorRole,
                                                      String correlationId) {
        if (actorUserId == null || actorRole == null
                || !(AppConstants.ROLE_STAFF.equalsIgnoreCase(actorRole)
                || AppConstants.ROLE_ADMIN.equalsIgnoreCase(actorRole))) {
            throw new BusinessException(403, "Pharmacy staff or ADMIN access is required");
        }

        Prescription prescription = findPrescriptionOrThrow(id);
        if (prescription.getStatus() == PrescriptionStatus.DISPENSED) {
            boolean changed = false;
            if (prescription.getDispensingServicePointId() == null
                    || prescription.getDispensingServicePointId().isBlank()) {
                prescription.setDispensingServicePointId(directoryClient.getActivePharmacyServicePointId());
                changed = true;
            }
            if (prescription.getConfirmedAt() == null) {
                prescription.setConfirmedAt(prescription.getCreatedAt() != null
                        ? prescription.getCreatedAt() : Instant.now());
                changed = true;
            }
            if (changed) prescriptionRepository.save(prescription);
            return prescriptionMapper.toResponse(prescription);
        }
        if (prescription.getStatus() != PrescriptionStatus.CONFIRMED) {
            throw new BusinessException(409, "Only a confirmed prescription can be dispensed");
        }
        if (prescription.getDispensingServicePointId() == null
                || prescription.getDispensingServicePointId().isBlank()) {
            // Backfill legacy confirmed prescriptions created before the Directory-backed field existed.
            prescription.setDispensingServicePointId(directoryClient.getActivePharmacyServicePointId());
        }
        if (prescription.getConfirmedAt() == null) {
            prescription.setConfirmedAt(prescription.getCreatedAt() != null
                    ? prescription.getCreatedAt() : Instant.now());
        }

        QueueExecutionClient.QueueEntryState entry = queueExecutionClient
                .getPharmacyEntry(id, actorUserId, actorRole);
        if (entry.getPatientId() == null || !prescription.getPatientId().equals(entry.getPatientId())) {
            throw new BusinessException(409,
                    "Hồ sơ bệnh nhân trong hàng đợi không khớp với toa thuốc");
        }
        if (!id.equals(entry.getPrescriptionId())
                || !"PHARMACY_DISPENSING".equalsIgnoreCase(entry.getType())
                || !"IN_PROGRESS".equalsIgnoreCase(entry.getQueueStatus())
                || !prescription.getDispensingServicePointId().equalsIgnoreCase(entry.getServicePointId())) {
            throw new BusinessException(409,
                    "Pharmacy queue entry must be IN_PROGRESS at the assigned dispensing service point");
        }

        queueExecutionClient.completePharmacyEntry(id, actorUserId, actorRole, correlationId);
        Instant dispensedAt = Instant.now();
        prescription.setStatus(PrescriptionStatus.DISPENSED);
        prescription.setDispensedAt(dispensedAt);
        prescription.setDispensedByUserId(actorUserId);
        Prescription saved = prescriptionRepository.save(prescription);

        Map<String, Object> eventPayload = new LinkedHashMap<>();
        eventPayload.put("prescriptionId", saved.getId());
        eventPayload.put("consultationId", saved.getConsultationId());
        eventPayload.put("patientId", saved.getPatientId());
        eventPayload.put("doctorId", saved.getDoctorId());
        eventPayload.put("dispensingServicePointId", saved.getDispensingServicePointId());
        eventPayload.put("dispensedByUserId", actorUserId);
        eventPayload.put("dispensedAt", dispensedAt);
        EventEnvelope envelope = new EventEnvelope(
                UUID.randomUUID(), "PrescriptionDispensed", 1, saved.getId(), 1,
                dispensedAt, "prescription-service", correlationId, objectMapper.valueToTree(eventPayload));
        rabbitTemplate.convertAndSend(AppConstants.EXCHANGE_PRESCRIPTION,
                "prescription.dispensed", envelope);
        return prescriptionMapper.toResponse(saved);
    }

    public PrescriptionResponse confirmPrescription(UUID id) {
        Prescription prescription = findPrescriptionOrThrow(id);
        return confirmPrescription(id, prescription.getDoctorId(), AppConstants.ROLE_DOCTOR);
    }

    /**
     * Danh sách đơn thuốc theo phiên khám.
     */
    @Transactional(readOnly = true)
    public List<PrescriptionResponse> getByConsultation(UUID consultationId) {
        return prescriptionRepository.findByConsultationId(consultationId)
                .stream()
                .map(prescriptionMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PrescriptionResponse> getByConsultation(UUID consultationId, UUID actorUserId, String actorRole) {
        return prescriptionRepository.findByConsultationId(consultationId).stream()
                .filter(prescription -> canRead(prescription, actorUserId, actorRole))
                .map(prescriptionMapper::toResponse)
                .toList();
    }

    /**
     * Lịch sử đơn thuốc của bệnh nhân.
     */
    @Transactional(readOnly = true)
    public List<PrescriptionResponse> getByPatient(UUID patientId) {
        return prescriptionRepository.findByPatientIdOrderByCreatedAtDesc(patientId)
                .stream()
                .map(prescriptionMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PrescriptionResponse> getByPatient(UUID patientId, UUID actorUserId, String actorRole) {
        if (AppConstants.ROLE_PATIENT.equalsIgnoreCase(actorRole)) {
            UUID ownPatientId = patientIdentityClient.patientIdForUser(actorUserId, actorRole);
            if (!patientId.equals(ownPatientId)) {
                throw new BusinessException(403, "Cannot read another patient's prescriptions");
            }
            return prescriptionRepository.findByPatientIdOrderByCreatedAtDesc(patientId)
                    .stream()
                    .filter(value -> value.getStatus() == PrescriptionStatus.CONFIRMED
                            || value.getStatus() == PrescriptionStatus.DISPENSED)
                    .map(prescriptionMapper::toResponse)
                    .toList();
        } else if (!AppConstants.ROLE_DOCTOR.equalsIgnoreCase(actorRole)
                && !AppConstants.ROLE_ADMIN.equalsIgnoreCase(actorRole)) {
            throw new BusinessException(403, "Prescription history access is not allowed");
        }
        if (AppConstants.ROLE_DOCTOR.equalsIgnoreCase(actorRole)) {
            return prescriptionRepository.findByPatientIdOrderByCreatedAtDesc(patientId)
                    .stream()
                    .filter(value -> actorUserId.equals(value.getDoctorId()))
                    .map(prescriptionMapper::toResponse)
                    .toList();
        }
        return getByPatient(patientId);
    }

    /**
     * Lay danh muc thuoc tu CSDL bảng drugs_dictionary.
     */
    @Transactional(readOnly = true)
    public List<MedicineInfo> getMedicineCatalog() {
        return drugRepository.findByActiveTrueOrderByNameAsc()
                .stream()
                .map(drug -> new MedicineInfo(drug.getCode(), drug.getName(), drug.getUnit(), drug.getDescription()))
                .toList();
    }

    private Prescription findPrescriptionOrThrow(UUID id) {
        return prescriptionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Prescription", "id", id));
    }

    private boolean canRead(Prescription prescription, UUID actorUserId, String actorRole) {
        if (actorUserId == null || actorRole == null) return false;
        if (AppConstants.ROLE_ADMIN.equalsIgnoreCase(actorRole)) return true;
        if (AppConstants.ROLE_DOCTOR.equalsIgnoreCase(actorRole)) {
            return actorUserId.equals(prescription.getDoctorId());
        }
        if (AppConstants.ROLE_PATIENT.equalsIgnoreCase(actorRole)) {
            UUID ownPatientId = patientIdentityClient.patientIdForUser(actorUserId, actorRole);
            return ownPatientId.equals(prescription.getPatientId())
                    && (prescription.getStatus() == PrescriptionStatus.CONFIRMED
                    || prescription.getStatus() == PrescriptionStatus.DISPENSED);
        }
        return false;
    }

    private void requireReadAccess(Prescription prescription, UUID actorUserId, String actorRole) {
        if (!canRead(prescription, actorUserId, actorRole)) {
            throw new BusinessException(403, "You are not allowed to view this prescription");
        }
    }

    public record MedicineInfo(String code, String name, String unit, String description) {}
}
