package com.careflow.prescription.config;

import com.careflow.common.dto.ApiResponse;
import com.careflow.common.exception.BusinessException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.UUID;

@Component
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
        return getConsultation(consultationId).status();
    }

    public ConsultationContext getConsultation(UUID consultationId) {
        var headers = PrescriptionRequestHeaders.current();
        try {
            ResponseEntity<ApiResponse<ConsultationContext>> response = restTemplate.exchange(
                    consultationServiceUrl + "/api/consultations/" + consultationId,
                    HttpMethod.GET, new HttpEntity<>(headers), new ParameterizedTypeReference<>() {});
            ConsultationContext context = response.getBody() == null ? null : response.getBody().getData();
            if (context != null && consultationId.equals(context.id()) && context.patientId() != null
                    && context.doctorId() != null && context.status() != null) {
                return context;
            }
        } catch (HttpStatusCodeException exception) {
            int status = exception.getStatusCode().value();
            if (status == 401 || status == 403 || status == 404) {
                throw new BusinessException(status, "Consultation access could not be verified");
            }
            throw new BusinessException(503, "Consultation service is unavailable");
        } catch (RestClientException exception) {
            throw new BusinessException(503, "Consultation service is unavailable");
        }
        throw new BusinessException(503, "Consultation service returned incomplete identity or status");
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ConsultationContext(UUID id, UUID patientId, UUID doctorId, String status) {}
}
