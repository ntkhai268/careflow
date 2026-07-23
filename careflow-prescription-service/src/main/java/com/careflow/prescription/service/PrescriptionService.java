package com.careflow.prescription.service;

import com.careflow.common.constants.AppConstants;
import com.careflow.common.exception.BusinessException;
import com.careflow.common.exception.ResourceNotFoundException;
import com.careflow.prescription.config.AppointmentClient;
import com.careflow.prescription.config.ConsultationClient;
import com.careflow.prescription.dto.request.CreatePrescriptionRequest;
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

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PrescriptionService {

    private final PrescriptionRepository prescriptionRepository;
    private final DrugRepository drugRepository;
    private final PrescriptionMapper prescriptionMapper;
    private final ConsultationClient consultationClient;
    private final AppointmentClient appointmentClient;
    private final RabbitTemplate rabbitTemplate;

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
    public PrescriptionResponse getPrescription(UUID id) {
        Prescription prescription = findPrescriptionOrThrow(id);
        return prescriptionMapper.toResponse(prescription);
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
    public PrescriptionResponse confirmPrescription(UUID id) {
        Prescription prescription = findPrescriptionOrThrow(id);

        if (prescription.getStatus() != PrescriptionStatus.DRAFT) {
            throw new BusinessException(400, "Cannot confirm prescription with status: " + prescription.getStatus());
        }

        if (prescription.getItems().isEmpty()) {
            throw new BusinessException(400, "Cannot confirm prescription with no medicine items");
        }

        // Validate consultation status
        validateConsultationStatus(prescription.getConsultationId());

        prescription.setStatus(PrescriptionStatus.CONFIRMED);
        Prescription saved = prescriptionRepository.save(prescription);

        log.info("Confirmed prescription {} — publishing event", saved.getId());

        // Publish event PrescriptionCreated lên RabbitMQ
        rabbitTemplate.convertAndSend(
            AppConstants.EXCHANGE_PRESCRIPTION,
            AppConstants.RK_PRESCRIPTION_CREATED,
            prescriptionMapper.toResponse(saved)
        );

        // Tự động kích hoạt tạo lịch hẹn tái khám nếu có followUpDate
        if (saved.getFollowUpDate() != null) {
            appointmentClient.createFollowUpAppointment(
                saved.getPatientId(),
                saved.getDoctorId(),
                saved.getFollowUpDate(),
                saved.getNotes()
            );
        }

        return prescriptionMapper.toResponse(saved);
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

    public record MedicineInfo(String code, String name, String unit, String description) {}
}
