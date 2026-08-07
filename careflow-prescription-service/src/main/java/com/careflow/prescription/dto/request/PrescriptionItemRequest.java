package com.careflow.prescription.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PrescriptionItemRequest {

    @NotBlank(message = "Medicine name is required")
    private String medicineName;

    private String medicineCode;
    private String unit;
    private String dosage;
    private String frequency;
    private String timing;
    private Integer duration;
    private Integer quantity;
    private String notes;
}
