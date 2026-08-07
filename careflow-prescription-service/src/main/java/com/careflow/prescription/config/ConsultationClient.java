package com.careflow.prescription.config;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.UUID;

@Component
@Slf4j
public class ConsultationClient {

    private final RestTemplate restTemplate;
    @Value("${app.consultation-service.url:http://careflow-consultation-service:8086}")
    private String consultationServiceUrl;

    public ConsultationClient(RestTemplateBuilder builder) {
        this.restTemplate = builder
                .setConnectTimeout(Duration.ofSeconds(2))
                .setReadTimeout(Duration.ofSeconds(3))
                .build();
    }

    public String getConsultationStatus(UUID consultationId) {
        try {
            String url = consultationServiceUrl + "/api/consultations/" + consultationId + "/status";
            log.info("Fetching consultation status from: {}", url);
            ResponseEntity<ConsultationApiResponse> response = restTemplate.getForEntity(url, ConsultationApiResponse.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null && response.getBody().getData() != null) {
                return response.getBody().getData();
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
        private String data;
    }
}
