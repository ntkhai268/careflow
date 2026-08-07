package com.careflow.prescription.config;

import com.careflow.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Component
@Slf4j
public class AppointmentClient {
    private final RestTemplate restTemplate;

    @Value("${app.appointment-service.url:http://careflow-appointment-service:8083}")
    private String appointmentServiceUrl;

    public AppointmentClient(RestTemplateBuilder builder) {
        this.restTemplate = builder
                .setConnectTimeout(Duration.ofSeconds(2))
                .setReadTimeout(Duration.ofSeconds(3))
                .build();
    }

    public void createFollowUpAppointment(UUID patientId, UUID doctorId, UUID consultationId,
                                          LocalDate followUpDate, String notes) {
        createFollowUpAppointment(patientId, doctorId, consultationId, followUpDate, notes, doctorId);
    }

    public void createFollowUpAppointment(UUID patientId, UUID doctorId, UUID consultationId,
                                          LocalDate followUpDate, String notes, UUID actorUserId) {
        if (followUpDate == null) return;
        if (consultationId == null) {
            throw new BusinessException(422, "Follow-up requires a consultation ID");
        }

        try {
            String url = appointmentServiceUrl + "/api/appointments/follow-ups";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-User-Id", actorUserId.toString());
            headers.set("X-User-Role", "DOCTOR");
            headers.set("Idempotency-Key", "follow-up-" + consultationId + "-" + followUpDate);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("consultationId", consultationId.toString());
            requestBody.put("patientId", patientId.toString());
            requestBody.put("department", "NOI_TONG_QUAT");
            requestBody.put("recommendedDate", followUpDate.toString());
            requestBody.put("timeSlot", "09:00-09:30");
            requestBody.put("note", "Hẹn tái khám từ đơn thuốc. Ghi chú: " + (notes == null ? "" : notes));

            restTemplate.postForObject(url, new HttpEntity<>(requestBody, headers), String.class);
            log.info("Successfully requested follow-up appointment for patient {} on date {}",
                    patientId, followUpDate);
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            log.error("Failed to automatically create follow-up appointment via appointment-service", exception);
            throw new BusinessException(502, "Không thể tạo lịch hẹn tái khám");
        }
    }
}
