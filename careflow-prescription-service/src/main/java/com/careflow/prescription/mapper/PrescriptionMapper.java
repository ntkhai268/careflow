package com.careflow.prescription.mapper;

import com.careflow.prescription.dto.request.CreatePrescriptionRequest;
import com.careflow.prescription.dto.request.PrescriptionItemRequest;
import com.careflow.prescription.dto.response.PrescriptionItemResponse;
import com.careflow.prescription.dto.response.PrescriptionResponse;
import com.careflow.prescription.model.Prescription;
import com.careflow.prescription.model.PrescriptionItem;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Component
public class PrescriptionMapper {

    private LocalDateTime toLocalDateTime(Instant instant) {
        return instant != null ? LocalDateTime.ofInstant(instant, ZoneId.systemDefault()) : null;
    }

    public PrescriptionResponse toResponse(Prescription entity) {
        List<PrescriptionItemResponse> itemResponses = entity.getItems().stream()
                .map(this::toItemResponse)
                .toList();

        return PrescriptionResponse.builder()
                .id(entity.getId())
                .consultationId(entity.getConsultationId())
                .patientId(entity.getPatientId())
                .doctorId(entity.getDoctorId())
                .diagnosis(entity.getDiagnosis())
                .notes(entity.getNotes())
                .followUpDate(entity.getFollowUpDate())
                .dispensingServicePointId(entity.getDispensingServicePointId())
                .confirmedAt(toLocalDateTime(entity.getConfirmedAt()))
                .dispensedAt(toLocalDateTime(entity.getDispensedAt()))
                .dispensedByUserId(entity.getDispensedByUserId())
                .replacesPrescriptionId(entity.getReplacesPrescriptionId())
                .cancellationReason(entity.getCancellationReason())
                .cancelledAt(toLocalDateTime(entity.getCancelledAt()))
                .cancelledByUserId(entity.getCancelledByUserId())
                .status(entity.getStatus())
                .items(itemResponses)
                .createdAt(toLocalDateTime(entity.getCreatedAt()))
                .updatedAt(toLocalDateTime(entity.getUpdatedAt()))
                .build();
    }

    public PrescriptionItemResponse toItemResponse(PrescriptionItem item) {
        return PrescriptionItemResponse.builder()
                .id(item.getId())
                .medicineName(item.getMedicineName())
                .medicineCode(item.getMedicineCode())
                .unit(item.getUnit())
                .dosage(item.getDosage())
                .frequency(item.getFrequency())
                .timing(item.getTiming())
                .duration(item.getDuration())
                .quantity(item.getQuantity())
                .notes(item.getNotes())
                .createdAt(toLocalDateTime(item.getCreatedAt()))
                .build();
    }

    public PrescriptionItem toItemEntity(PrescriptionItemRequest request) {
        return PrescriptionItem.builder()
                .medicineName(request.getMedicineName())
                .medicineCode(request.getMedicineCode())
                .unit(request.getUnit())
                .dosage(request.getDosage())
                .frequency(request.getFrequency())
                .timing(request.getTiming())
                .duration(request.getDuration())
                .quantity(request.getQuantity())
                .notes(request.getNotes())
                .build();
    }
}
