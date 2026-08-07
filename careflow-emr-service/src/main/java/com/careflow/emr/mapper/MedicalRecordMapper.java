package com.careflow.emr.mapper;

import com.careflow.emr.dto.response.MedicalRecordResponse;
import com.careflow.emr.model.MedicalRecord;
import org.springframework.stereotype.Component;

@Component
public class MedicalRecordMapper {

    public MedicalRecordResponse toResponse(MedicalRecord entity) {
        if (entity == null) {
            return null;
        }
        return MedicalRecordResponse.builder()
                .id(entity.getId())
                .patientId(entity.getPatientId())
                .recordNumber(entity.getRecordNumber())
                .bloodType(entity.getBloodType())
                .medicalHistory(entity.getMedicalHistory())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
