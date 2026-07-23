package com.careflow.prescription.config;

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

    @Value("${app.appointment-service.url:http://localhost:8084}")
    private String appointmentServiceUrl;

    public AppointmentClient(RestTemplateBuilder builder) {
        this.restTemplate = builder
                .setConnectTimeout(Duration.ofSeconds(2))
                .setReadTimeout(Duration.ofSeconds(3))
                .build();
    }

    /**
     * Tự động tạo cuộc hẹn tái khám mới qua appointment-service khi xác nhận đơn thuốc.
     */
    public void createFollowUpAppointment(UUID patientId, UUID doctorId, LocalDate followUpDate, String notes) {
        if (followUpDate == null) return;

        try {
            String url = appointmentServiceUrl + "/api/appointments";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("patientId", patientId.toString());
            requestBody.put("doctorId", doctorId.toString());
            requestBody.put("appointmentDate", followUpDate.toString());
            requestBody.put("type", "FOLLOW_UP");
            requestBody.put("notes", "Hẹn tái khám từ đơn thuốc. Ghi chú: " + (notes != null ? notes : ""));

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

            restTemplate.postForObject(url, request, String.class);
            log.info("Successfully requested follow-up appointment for patient {} on date {}", patientId, followUpDate);
        } catch (Exception e) {
            // Log warning but don't break prescription confirmation flow if appointment-service is unreachable
            log.warn("Failed to automatically create follow-up appointment via appointment-service: {}", e.getMessage());
        }
    }
}
