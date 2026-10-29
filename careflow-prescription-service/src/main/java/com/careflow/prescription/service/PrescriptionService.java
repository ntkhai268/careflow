package com.careflow.prescription.service;

import com.careflow.common.constants.AppConstants;
import com.careflow.common.exception.BusinessException;
import com.careflow.common.exception.ResourceNotFoundException;
import com.careflow.common.event.EventEnvelope;
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
    private final DirectoryClient directoryClient;
    private final QueueExecutionClient queueExecutionClient;
    private final PatientIdentityClient patientIdentityClient;
    private final ObjectMapper objectMapper;
    private final PrescriptionEventOutbox eventOutbox;
    private final PrescriptionAiReleaseGuard aiReleaseGuard;

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

    private String verifiedConsultationStatus(UUID consultationId, UUID patientId, UUID doctorId) {
        var context = consultationClient.getConsultation(consultationId);
        if (context == null || !consultationId.equals(context.id()) || context.patientId() == null
                || context.doctorId() == null || context.status() == null
                || !java.util.Set.of("IN_PROGRESS", "AWAITING_CLS", "AWAITING_REVIEW", "READY_TO_COMPLETE",
                    "COMPLETED", "CANCELLED", "TRANSFERRED").contains(context.status())) {
            throw new BusinessException(503, "Consultation identity and status could not be verified");
        }
        if (!doctorId.equals(context.doctorId()) || !patientId.equals(context.patientId())) {
            throw new BusinessException(403, "Prescription patient and doctor must match the consultation");
        }
        return context.status();
    }

    private void validateConsultationStatus(UUID consultationId, UUID patientId, UUID doctorId) {
        String status = verifiedConsultationStatus(consultationId, patientId, doctorId);
        if (status.equals("COMPLETED") || status.equals("CANCELLED") || status.equals("TRANSFERRED")) {
            throw new BusinessException(400, "Không thể thao tác đơn thuốc vì phiên khám đã hoàn tất hoặc bị hủy (Trạng thái: " + status + ")");
        }
    }

    private void validateMedicineItems(List<PrescriptionItemRequest> items) {
        if (items == null) return;
        for (var item : items) {
            if (item.getMedicineCode() == null || item.getMedicineCode().isBlank()) {
                validateNamedDraft(item);
                continue;
            }
            if (!drugRepository.existsByCode(item.getMedicineCode())) {
                throw new BusinessException(400, "Thuốc " + item.getMedicineName() + " (Mã: " + item.getMedicineCode() + ") không nằm trong danh mục được phép kê của bệnh viện.");
            }
        }
    }

    private void validateNamedDraft(PrescriptionItemRequest item) {
        if (item.getMedicineName() == null || item.getMedicineName().isBlank() || item.getMedicineName().length() > 255) {
            throw new BusinessException(422, "Nhập tên thuốc trước khi lưu toa nháp.");
        }
        item.setMedicineCode(null);
        if (item.getQuantity() != null && item.getQuantity() <= 0 || item.getDuration() != null && item.getDuration() <= 0) {
            throw new BusinessException(422, "Số lượng và số ngày phải lớn hơn 0 hoặc để trống khi chưa xác định.");
        }
    }

    private void validateCompletedConsultationForAmendment(UUID consultationId, UUID patientId, UUID doctorId) {
        String status = verifiedConsultationStatus(consultationId, patientId, doctorId);
        if (status.equals("CANCELLED") || status.equals("TRANSFERRED")) {
            throw new BusinessException(400, "Cannot amend a prescription for a cancelled consultation");
        }
    }

    /**
     * Tạo đơn thuốc mới với danh sách thuốc kèm theo.
     * Status mặc định: DRAFT — bác sỹ có thể chỉnh sửa trước khi xác nhận.
     */
    @Transactional
    public PrescriptionResponse createPrescription(CreatePrescriptionRequest request) {
        // Validate consultation status
        validateConsultationStatus(request.getConsultationId(), request.getPatientId(), request.getDoctorId());

        // Validate medicine codes
        validateMedicineItems(request.getItems());

        Prescription prescription = Prescription.builder()
                .consultationId(request.getConsultationId())
                .patientId(request.getPatientId())
                .doctorId(request.getDoctorId())
                .diagnosis(request.getDiagnosis())
                .notes(request.getNotes())
                .status(PrescriptionStatus.DRAFT)
                .build();
        prescription.requestFollowUp(request.getFollowUpDate(), request.getFollowUpDepartment(),
                request.getFollowUpTimeSlot());
        prescription.replaceItems(toItemEntities(request.getItems()));

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

    /**
     * Xác nhận đơn thuốc (DRAFT → CONFIRMED).
     * Sau khi xác nhận, đơn thuốc không thể sửa nữa.
     * Sẽ publish event PrescriptionCreated lên RabbitMQ.
     */
    @Transactional
    public PrescriptionResponse confirmPrescription(UUID id, UUID actorUserId, String actorRole) {
        requireDoctor(actorUserId, actorRole);
        Prescription prescription = findPrescriptionForUpdateOrThrow(id);
        if (!actorUserId.equals(prescription.getDoctorId())) {
            throw new BusinessException(403, "Doctor is not assigned to this prescription");
        }

        if (prescription.getStatus() == PrescriptionStatus.CONFIRMED
                || prescription.getStatus() == PrescriptionStatus.DISPENSED) {
            validateCompletedConsultationForAmendment(prescription.getConsultationId(),
                    prescription.getPatientId(), prescription.getDoctorId());
            return prescriptionMapper.toResponse(prescription);
        }

        if (prescription.getStatus() != PrescriptionStatus.DRAFT) {
            throw new BusinessException(400, "Cannot confirm prescription with status: " + prescription.getStatus());
        }

        if (prescription.getItems().isEmpty()) {
            throw new BusinessException(400, "Cannot confirm prescription with no medicine items");
        }

        // Validate consultation status
        if (prescription.getReplacesPrescriptionId() != null) {
            validateCompletedConsultationForAmendment(prescription.getConsultationId(), prescription.getPatientId(), prescription.getDoctorId());
        } else {
            validateConsultationStatus(prescription.getConsultationId(), prescription.getPatientId(), prescription.getDoctorId());
        }

        aiReleaseGuard.validate(prescription);
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
        appendEvent(saved,AppConstants.RK_PRESCRIPTION_ISSUED,envelope);

        return response;
    }

    @Transactional
    public PrescriptionResponse cancelPrescription(UUID id, String reason,
                                                   UUID actorUserId, String actorRole) {
        if (actorUserId == null || actorRole == null
                || !AppConstants.ROLE_DOCTOR.equalsIgnoreCase(actorRole)) {
            throw new BusinessException(403, "Assigned doctor access is required");
        }

        Prescription prescription = findPrescriptionForUpdateOrThrow(id);
        if (AppConstants.ROLE_DOCTOR.equalsIgnoreCase(actorRole)
                && !actorUserId.equals(prescription.getDoctorId())) {
            throw new BusinessException(403, "Only the assigned doctor can cancel this prescription");
        }
        if (AppConstants.ROLE_DOCTOR.equalsIgnoreCase(actorRole) && !canRead(prescription, actorUserId, actorRole)) {
            throw new BusinessException(403, "Doctor is no longer responsible for this consultation");
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

    /**
     * Cập nhật đơn thuốc (chỉ khi status = DRAFT).
     * Xóa toàn bộ items cũ và thay bằng items mới từ request.
     */
    @Transactional
    public PrescriptionResponse updatePrescription(UUID id, CreatePrescriptionRequest request,
                                                   UUID actorUserId, String actorRole) {
        requireDoctor(actorUserId, actorRole);
        Prescription prescription = findPrescriptionForUpdateOrThrow(id);
        if (!actorUserId.equals(prescription.getDoctorId())) {
            throw new BusinessException(403, "Only the assigned doctor can update this prescription");
        }
        if ((request.getDoctorId() != null && !actorUserId.equals(request.getDoctorId()))
                || !prescription.getPatientId().equals(request.getPatientId())
                || !prescription.getConsultationId().equals(request.getConsultationId())) {
            throw new BusinessException(403, "Cannot change the prescription patient, doctor or consultation");
        }
        request.setDoctorId(actorUserId);

        if (prescription.getStatus() != PrescriptionStatus.DRAFT) {
            throw new BusinessException(400, "Cannot update prescription with status: " + prescription.getStatus());
        }

        // Validate consultation status
        validateConsultationStatus(prescription.getConsultationId(), prescription.getPatientId(), prescription.getDoctorId());

        // Validate medicine codes
        validateMedicineItems(request.getItems());

        // Cập nhật thông tin chung
        prescription.setDiagnosis(request.getDiagnosis());
        prescription.setNotes(request.getNotes());
        prescription.requestFollowUp(request.getFollowUpDate(), request.getFollowUpDepartment(),
                request.getFollowUpTimeSlot());

        // Xóa items cũ, thêm items mới
        prescription.replaceItems(toItemEntities(request.getItems()));

        Prescription saved = prescriptionRepository.save(prescription);
        log.info("Updated prescription {} with {} items", saved.getId(), saved.getItems().size());

        return prescriptionMapper.toResponse(saved);
    }

    @Transactional
    public PrescriptionResponse amendPrescription(UUID id, AmendPrescriptionRequest request,
                                                  UUID actorUserId, String actorRole) {
        requireDoctor(actorUserId, actorRole);
        Prescription original = findPrescriptionForUpdateOrThrow(id);
        if (!actorUserId.equals(original.getDoctorId())) {
            throw new BusinessException(403, "Only the assigned doctor can amend this prescription");
        }
        if (original.getStatus() != PrescriptionStatus.CONFIRMED) {
            throw new BusinessException(409, "Only a confirmed prescription can be amended");
        }

        validateCompletedConsultationForAmendment(original.getConsultationId(), original.getPatientId(), original.getDoctorId());
        validateMedicineItems(request.getItems());
        Prescription replacement = Prescription.builder()
                .consultationId(original.getConsultationId())
                .patientId(original.getPatientId())
                .doctorId(original.getDoctorId())
                .diagnosis(request.getDiagnosis())
                .notes(request.getNotes())
                .replacesPrescriptionId(original.getId())
                .status(PrescriptionStatus.DRAFT)
                .build();
        replacement.requestFollowUp(request.getFollowUpDate(), request.getFollowUpDepartment(),
                request.getFollowUpTimeSlot());
        replacement.replaceItems(toItemEntities(request.getItems()));

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
        appendEvent(prescription,AppConstants.RK_PRESCRIPTION_CANCELLED,envelope);
    }

    @Transactional
    public PrescriptionResponse dispensePrescription(UUID id, UUID actorUserId, String actorRole,
                                                      String correlationId) {
        if (actorUserId == null || actorRole == null
                || !AppConstants.ROLE_STAFF.equalsIgnoreCase(actorRole)) {
            throw new BusinessException(403, "Assigned pharmacy staff access is required");
        }

        Prescription prescription = findPrescriptionForUpdateOrThrow(id);
        if(prescription.getStatus()==PrescriptionStatus.DISPENSED
                && !actorUserId.equals(prescription.getDispensedByUserId()))
            throw new BusinessException(403,"Only the dispensing staff member may repeat this operation");
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
        appendEvent(saved,"prescription.dispensed",envelope);
        return prescriptionMapper.toResponse(saved);
    }

    /**
     * Danh sách đơn thuốc theo phiên khám.
     */
    @Transactional(readOnly = true)
    public List<PrescriptionResponse> getByConsultation(UUID consultationId, UUID actorUserId, String actorRole) {
        requireClinicalReader(actorUserId, actorRole);
        return prescriptionRepository.findByConsultationId(consultationId).stream()
                .filter(prescription -> canRead(prescription, actorUserId, actorRole))
                .map(prescriptionMapper::toResponse)
                .toList();
    }

    /**
     * Lịch sử đơn thuốc của bệnh nhân.
     */
    /**
     * Lịch sử đơn thuốc của bệnh nhân.
     */
    @Transactional(readOnly = true)
    public List<PrescriptionResponse> getByPatient(UUID patientId, UUID actorUserId, String actorRole) {
        requireClinicalReader(actorUserId, actorRole);
        patientIdentityClient.requirePatientAccess(patientId, actorUserId, actorRole);
        if (AppConstants.ROLE_PATIENT.equalsIgnoreCase(actorRole)) {
            return prescriptionRepository.findByPatientIdOrderByCreatedAtDesc(patientId)
                    .stream()
                    .filter(value -> value.getStatus() == PrescriptionStatus.CONFIRMED
                            || value.getStatus() == PrescriptionStatus.DISPENSED)
                    .map(prescriptionMapper::toResponse)
                    .toList();
        } else {
            return prescriptionRepository.findByPatientIdOrderByCreatedAtDesc(patientId)
                    .stream()
                    .filter(value -> actorUserId.equals(value.getDoctorId()))
                    .map(prescriptionMapper::toResponse)
                    .toList();
        }
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

    private List<PrescriptionItem> toItemEntities(List<PrescriptionItemRequest> requests) {
        return requests.stream().map(prescriptionMapper::toItemEntity).toList();
    }

    private Prescription findPrescriptionForUpdateOrThrow(UUID id) {
        return prescriptionRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Prescription", "id", id));
    }

    private Prescription findPrescriptionOrThrow(UUID id) {
        return prescriptionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Prescription", "id", id));
    }

    private boolean canRead(Prescription prescription, UUID actorUserId, String actorRole) {
        if (actorUserId == null || actorRole == null) return false;
        if (AppConstants.ROLE_DOCTOR.equalsIgnoreCase(actorRole)) {
            try {
                var consultation = consultationClient.getConsultation(prescription.getConsultationId());
                if (consultation == null) throw new BusinessException(503, "Cannot verify current consultation responsibility");
                return actorUserId.equals(consultation.doctorId())
                        && prescription.getPatientId().equals(consultation.patientId());
            } catch (BusinessException exception) {
                if (exception.getStatus() == 403 || exception.getStatus() == 404) return false;
                throw exception;
            }
        }
        if (AppConstants.ROLE_PATIENT.equalsIgnoreCase(actorRole)) {
            if (prescription.getStatus() != PrescriptionStatus.CONFIRMED
                    && prescription.getStatus() != PrescriptionStatus.DISPENSED) return false;
            try {
                patientIdentityClient.requirePatientAccess(prescription.getPatientId(), actorUserId, actorRole);
            } catch (BusinessException exception) {
                if (exception.getStatus() == 403 || exception.getStatus() == 404) return false;
                throw exception;
            }
            return true;
        }
        return false;
    }

    private void requireClinicalReader(UUID actorUserId, String actorRole) {
        if (actorUserId == null || !(AppConstants.ROLE_PATIENT.equalsIgnoreCase(actorRole)
                || AppConstants.ROLE_DOCTOR.equalsIgnoreCase(actorRole))) {
            throw new BusinessException(403, "Patient owner or assigned doctor access is required");
        }
    }

    private void requireReadAccess(Prescription prescription, UUID actorUserId, String actorRole) {
        if (actorUserId != null && AppConstants.ROLE_STAFF.equalsIgnoreCase(actorRole)) {
            QueueExecutionClient.QueueEntryState entry = queueExecutionClient
                    .getPharmacyEntry(prescription.getId(), actorUserId, actorRole);
            boolean assignedToPharmacy = prescription.getId().equals(entry.getPrescriptionId())
                    && prescription.getPatientId().equals(entry.getPatientId())
                    && "PHARMACY_DISPENSING".equalsIgnoreCase(entry.getType())
                    && prescription.getDispensingServicePointId() != null
                    && prescription.getDispensingServicePointId().equalsIgnoreCase(entry.getServicePointId());
            if (assignedToPharmacy) return;
        }
        if (!canRead(prescription, actorUserId, actorRole)) {
            throw new BusinessException(403, "You are not allowed to view this prescription");
        }
    }

    private void appendEvent(Prescription prescription, String routingKey, EventEnvelope event) {
        // The outbox joins the JPA transaction, so events are published at-least-once.
        eventOutbox.append(prescription.getId(), event.eventType(), routingKey, event);
    }

    public record MedicineInfo(String code, String name, String unit, String description) {}
}
