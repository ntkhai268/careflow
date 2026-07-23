package com.careflow.prescription.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PrescriptionItemResponse {

    private UUID id;
    private String medicineName;
    private String medicineCode;
    private String unit;
    private String dosage;
    private String frequency;
    private String timing;
    private Integer duration;
    private Integer quantity;
    private String notes;
    private LocalDateTime createdAt;
}
