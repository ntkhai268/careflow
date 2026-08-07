package com.careflow.consultation.dto.response;

import com.careflow.consultation.model.ConsultationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConsultationResponse {

    private UUID id;
    private UUID appointmentId;
    private UUID patientId;
    private UUID doctorId;
    private String doctorName;
    private String departmentCode;
    private String departmentName;

    // Sinh hiệu
    private BigDecimal temperature;
    private String bloodPressure;
    private Integer heartRate;
    private Integer spo2;
    private BigDecimal height;
    private BigDecimal weight;

    // Lâm sàng
    private String symptoms;
    private String clinicalNotes;

    // Chẩn đoán
    private String icd10Code;
    private String icd10Name;
    private String diagnosis;

    // Trạng thái
    private ConsultationStatus status;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;

    // Audit
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
