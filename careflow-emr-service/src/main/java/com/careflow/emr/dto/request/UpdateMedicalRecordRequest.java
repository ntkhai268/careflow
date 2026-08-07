package com.careflow.emr.dto.request;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateMedicalRecordRequest {

    @Size(max = 10, message = "Nhóm máu tối đa 10 ký tự")
    private String bloodType;

    private String medicalHistory;
}
