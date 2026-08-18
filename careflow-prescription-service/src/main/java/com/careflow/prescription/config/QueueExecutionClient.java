package com.careflow.prescription.config;

import com.careflow.common.dto.ApiResponse;
import com.careflow.common.exception.BusinessException;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.UUID;

@Component
@Slf4j
public class QueueExecutionClient {

    private final RestTemplate restTemplate;

    @Value("${app.queue-service.url:http://careflow-queue-service:8084}")
    private String queueServiceUrl;

    public QueueExecutionClient(RestTemplateBuilder builder) {
        this.restTemplate = builder
                .setConnectTimeout(Duration.ofSeconds(2))
                .setReadTimeout(Duration.ofSeconds(3))
                .build();
    }

    public QueueEntryState getPharmacyEntry(UUID prescriptionId, UUID actorUserId, String actorRole) {
        HttpHeaders headers = headers(actorUserId, actorRole);
        try {
            ResponseEntity<ApiResponse<QueueEntryState>> response = restTemplate.exchange(
                    queueServiceUrl + "/api/queues/prescriptions/" + prescriptionId + "/current",
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    new org.springframework.core.ParameterizedTypeReference<>() { });
            ApiResponse<QueueEntryState> body = response.getBody();
            if (body == null || body.getData() == null) {
                throw new BusinessException(503, "Queue Service không trả về lượt phát thuốc");
            }
            return body.getData();
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            log.error("Cannot resolve pharmacy queue entry for prescription {}", prescriptionId, exception);
            throw new BusinessException(503, "Không thể kiểm tra lượt phát thuốc");
        }
    }

    public void completePharmacyEntry(UUID prescriptionId, UUID actorUserId, String actorRole,
                                      String correlationId) {
        HttpHeaders headers = headers(actorUserId, actorRole);
        if (correlationId != null && !correlationId.isBlank()) {
            headers.set("X-Correlation-Id", correlationId);
        }
        try {
            restTemplate.exchange(
                    queueServiceUrl + "/api/queues/prescriptions/" + prescriptionId + "/complete",
                    HttpMethod.POST,
                    new HttpEntity<>(headers),
                    Void.class);
        } catch (Exception exception) {
            log.error("Cannot complete pharmacy queue entry for prescription {}", prescriptionId, exception);
            throw new BusinessException(502, "Không thể hoàn tất lượt phát thuốc");
        }
    }

    private HttpHeaders headers(UUID actorUserId, String actorRole) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-User-Id", actorUserId.toString());
        headers.set("X-User-Role", actorRole);
        return headers;
    }

    @Data
    public static class QueueEntryState {
        private UUID entryId;
        private UUID patientId;
        private String queueStatus;
        private String type;
        private String servicePointId;
        private UUID prescriptionId;
    }
}
