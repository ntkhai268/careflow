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
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;

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

    public void requirePatientAccess(UUID patientId, UUID userId, String role) {
        if (userId == null || !("PATIENT".equalsIgnoreCase(role) || "DOCTOR".equalsIgnoreCase(role))) {
            throw new BusinessException(403, "Patient owner or assigned doctor access is required");
        }
        HttpHeaders headers = PrescriptionRequestHeaders.current();
        try {
            ApiResponse<PatientResponse> response = restTemplate.exchange(
                    patientServiceUrl + "/api/patients/" + patientId,
                    HttpMethod.GET, new HttpEntity<>(headers),
                    new org.springframework.core.ParameterizedTypeReference<ApiResponse<PatientResponse>>() {})
                    .getBody();
            PatientResponse patient = response == null ? null : response.getData();
            if (patient == null || !patientId.equals(patient.getId()) || patient.getUserId() == null) {
                throw new BusinessException(503, "Patient service returned incomplete identity");
            }
            if ("PATIENT".equalsIgnoreCase(role) && !userId.equals(patient.getUserId())) {
                throw new BusinessException(403, "Cannot read another patient's prescriptions");
            }
        } catch (HttpStatusCodeException exception) {
            int status = exception.getStatusCode().value();
            if (status == 401 || status == 403 || status == 404) {
                throw new BusinessException(status, "Patient access could not be verified");
            }
            throw new BusinessException(503, "Patient service is unavailable");
        } catch (RestClientException exception) {
            throw new BusinessException(503, "Patient service is unavailable");
        }
    }

    @Data
    public static class PatientResponse {
        private UUID id;
        private UUID userId;
    }
}
