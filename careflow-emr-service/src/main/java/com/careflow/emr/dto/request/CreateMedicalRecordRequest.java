package com.careflow.emr.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateMedicalRecordRequest {

    @NotNull(message = "ID bệnh nhân không được để trống")
    private UUID patientId;

    @Size(max = 10, message = "Nhóm máu tối đa 10 ký tự")
    private String bloodType;

    private String medicalHistory;
}
