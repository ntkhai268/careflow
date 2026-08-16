package com.careflow.lab.dto.response;

import com.careflow.lab.model.LabOrderItem;
import com.careflow.lab.model.LabOrderItemStatus;

import java.time.Instant;
import java.util.UUID;

public record LabOrderItemResponse(
        UUID id,
        String serviceCode,
        String serviceName,
        String servicePointId,
        boolean required,
        LabOrderItemStatus status,
        String resultValue,
        String referenceRange,
        String unit,
        String resultFlag,
        String comment,
        UUID performedByStaffId,
        Instant performedAt
) {
    public static LabOrderItemResponse from(LabOrderItem item) {
        return new LabOrderItemResponse(
                item.getId(),
                item.getServiceCode(),
                item.getServiceName(),
                item.getServicePointId(),
                item.isRequired(),
                item.getStatus(),
                item.getResultValue(),
                item.getReferenceRange(),
                item.getResultUnit(),
                item.getResultFlag(),
                item.getComment(),
                item.getPerformedByStaffId(),
                item.getPerformedAt());
    }
}
