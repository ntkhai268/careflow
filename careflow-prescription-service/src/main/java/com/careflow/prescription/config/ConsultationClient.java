package com.careflow.prescription.config;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.UUID;

@Component
@Slf4j
public class ConsultationClient {

    private final RestTemplate restTemplate;
    private static final String CONSULTATION_SERVICE_URL = "http://localhost:8086/api/consultations/";

    public ConsultationClient(RestTemplateBuilder builder) {
        this.restTemplate = builder
                .setConnectTimeout(Duration.ofSeconds(2))
                .setReadTimeout(Duration.ofSeconds(3))
                .build();
    }

    public String getConsultationStatus(UUID consultationId) {
        try {
            String url = CONSULTATION_SERVICE_URL + consultationId;
            log.info("Fetching consultation status from: {}", url);
            ResponseEntity<ConsultationApiResponse> response = restTemplate.getForEntity(url, ConsultationApiResponse.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null && response.getBody().getData() != null) {
                return response.getBody().getData().getStatus();
            }
        } catch (Exception e) {
            log.error("Failed to fetch consultation status for ID: {}. Error: {}", consultationId, e.getMessage());
        }
        // Fallback to null if service call fails or is not found
        return null;
    }

    @Data
    public static class ConsultationApiResponse {
        private String message;
        private ConsultationDto data;
    }

    @Data
    public static class ConsultationDto {
        private UUID id;
        private String status;
    }
}
