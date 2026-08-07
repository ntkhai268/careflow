package com.careflow.lab.dto.response;

import com.careflow.lab.model.LabOrder;
import com.careflow.lab.model.LabOrderStatus;
import com.careflow.lab.model.PaymentStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record LabOrderResponse(
        UUID id,
        UUID consultationId,
        UUID patientId,
        UUID orderedByDoctorId,
        UUID departmentId,
        LabOrderStatus status,
        PaymentStatus paymentStatus,
        String clinicalNote,
        List<LabOrderItemResponse> items,
        Instant createdAt,
        Instant updatedAt
) {
    public static LabOrderResponse from(LabOrder order) {
        return new LabOrderResponse(
                order.getId(),
                order.getConsultationId(),
                order.getPatientId(),
                order.getOrderedByDoctorId(),
                order.getDepartmentId(),
                order.getStatus(),
                order.getPaymentStatus(),
                order.getClinicalNote(),
                order.getItems().stream().map(LabOrderItemResponse::from).toList(),
                order.getCreatedAt(),
                order.getUpdatedAt());
    }
}
