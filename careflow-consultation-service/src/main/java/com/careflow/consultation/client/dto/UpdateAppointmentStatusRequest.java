package com.careflow.consultation.client.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateAppointmentStatusRequest {
    @NotBlank(message = "Trạng thái không được để trống")
    private String status;
    private String notes;
}
