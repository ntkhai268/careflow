package com.careflow.consultation.mapper;

import com.careflow.consultation.dto.request.UpdateConsultationRequest;
import com.careflow.consultation.dto.response.ConsultationResponse;
import com.careflow.consultation.model.Consultation;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Component
public class ConsultationMapper {

    private LocalDateTime toLocalDateTime(Instant instant) {
        return instant != null ? LocalDateTime.ofInstant(instant, ZoneId.systemDefault()) : null;
    }

    public ConsultationResponse toResponse(Consultation entity) {
        return ConsultationResponse.builder()
                .id(entity.getId())
                .appointmentId(entity.getAppointmentId())
                .patientId(entity.getPatientId())
                .doctorId(entity.getDoctorId())
                .temperature(entity.getTemperature())
                .bloodPressure(entity.getBloodPressure())
                .heartRate(entity.getHeartRate())
                .spo2(entity.getSpo2())
                .height(entity.getHeight())
                .weight(entity.getWeight())
                .symptoms(entity.getSymptoms())
                .clinicalNotes(entity.getClinicalNotes())
                .icd10Code(entity.getIcd10Code())
                .icd10Name(entity.getIcd10Name())
                .diagnosis(entity.getDiagnosis())
                .status(entity.getStatus())
                .startedAt(entity.getStartedAt())
                .completedAt(entity.getCompletedAt())
                .createdAt(toLocalDateTime(entity.getCreatedAt()))
                .updatedAt(toLocalDateTime(entity.getUpdatedAt()))
                .build();
    }

    /**
     * Cập nhật entity từ request DTO.
     * Chỉ cập nhật các field không null trong request (partial update).
     */
    public void updateEntityFromRequest(UpdateConsultationRequest request, Consultation entity) {
        if (request.getTemperature() != null) entity.setTemperature(request.getTemperature());
        if (request.getBloodPressure() != null) entity.setBloodPressure(request.getBloodPressure());
        if (request.getHeartRate() != null) entity.setHeartRate(request.getHeartRate());
        if (request.getSpo2() != null) entity.setSpo2(request.getSpo2());
        if (request.getHeight() != null) entity.setHeight(request.getHeight());
        if (request.getWeight() != null) entity.setWeight(request.getWeight());
        if (request.getSymptoms() != null) entity.setSymptoms(request.getSymptoms());
        if (request.getClinicalNotes() != null) entity.setClinicalNotes(request.getClinicalNotes());
        if (request.getIcd10Code() != null) entity.setIcd10Code(request.getIcd10Code());
        if (request.getIcd10Name() != null) entity.setIcd10Name(request.getIcd10Name());
        if (request.getDiagnosis() != null) entity.setDiagnosis(request.getDiagnosis());
    }
}
