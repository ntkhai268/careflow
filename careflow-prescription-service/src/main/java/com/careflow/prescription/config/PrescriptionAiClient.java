package com.careflow.prescription.config;

import com.careflow.common.exception.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;

@Component
public class PrescriptionAiClient {

    private static final Set<Integer> AUTHORIZATION_ERROR_CODES = Set.of(401, 403, 404, 409);

    private final RestTemplate http;
    private final String base;

    public PrescriptionAiClient(
            RestTemplateBuilder builder,
            @Value("${app.ai-service.url:https://careflow-ai-service:8092}") String base) {
        this.http = builder
                .connectTimeout(Duration.ofSeconds(3))
                .readTimeout(Duration.ofSeconds(5))
                .build();
        this.base = base;
    }

    public JsonNode job(UUID id) {
        try {
            var response = http.exchange(
                    base + "/api/ai/voice-prescription/jobs/" + id,
                    HttpMethod.GET,
                    new HttpEntity<>(PrescriptionRequestHeaders.current()),
                    JsonNode.class);
            JsonNode value = response.getBody();
            if (value == null || value.path("status").asInt() != 200 || !value.path("data").isObject()) {
                throw new BusinessException(503, "Kết quả AI không đúng contract");
            }
            return value.path("data");
        } catch (HttpStatusCodeException e) {
            int statusCode = e.getStatusCode().value();
            if (AUTHORIZATION_ERROR_CODES.contains(statusCode)) {
                throw new BusinessException(statusCode, "Không còn quyền đọc đề xuất AI");
            }
            throw new BusinessException(503, "AI Service chưa sẵn sàng");
        } catch (RestClientException e) {
            throw new BusinessException(503, "AI Service chưa sẵn sàng");
        }
    }
}
