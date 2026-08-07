package com.careflow.prescription.config;

import com.careflow.common.dto.ApiResponse;
import com.careflow.common.exception.BusinessException;
import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.UUID;

@Component
public class PatientIdentityClient {
    private final RestTemplate restTemplate;

    @Value("${app.patient-service.url:http://careflow-patient-service:8082}")
    private String patientServiceUrl;

    public PatientIdentityClient(RestTemplateBuilder builder) {
        this.restTemplate = builder
                .setConnectTimeout(Duration.ofSeconds(2))
                .setReadTimeout(Duration.ofSeconds(3))
                .build();
    }

    public UUID patientIdForUser(UUID userId, String role) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-User-Id", userId.toString());
        headers.set("X-User-Role", role);
        try {
            ApiResponse<PatientResponse> response = restTemplate.exchange(
                    patientServiceUrl + "/api/patients/user/" + userId,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    new org.springframework.core.ParameterizedTypeReference<ApiResponse<PatientResponse>>() { })
                    .getBody();
            if (response == null || response.getData() == null || response.getData().getId() == null) {
                throw new BusinessException(403, "Không xác định được hồ sơ bệnh nhân hiện tại");
            }
            return response.getData().getId();
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException(503, "Không thể xác minh hồ sơ bệnh nhân");
        }
    }

    @Data
    public static class PatientResponse {
        private UUID id;
    }
}
