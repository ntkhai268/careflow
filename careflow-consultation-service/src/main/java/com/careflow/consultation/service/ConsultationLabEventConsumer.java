package com.careflow.consultation.service;

import com.careflow.common.event.EventEnvelope;
import com.careflow.common.exception.BusinessException;
import com.careflow.consultation.config.RabbitMQConfig;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class ConsultationLabEventConsumer {

    private final ConsultationService consultationService;

    @RabbitListener(queues = RabbitMQConfig.LAB_RESULTS_QUEUE)
    @Transactional
    public void consume(EventEnvelope envelope) {
        if (envelope == null
                || envelope.payload() == null
                || !"AllRequiredResultsAvailable".equals(envelope.eventType())) {
            throw new BusinessException(422, "Invalid AllRequiredResultsAvailable event envelope");
        }
        JsonNode payload = envelope.payload();
        UUID consultationId = requiredUuid(payload, "consultationId");
        UUID patientId = requiredUuid(payload, "patientId");

        consultationService.markWaitingForReview(consultationId, patientId);
    }

    private UUID requiredUuid(JsonNode payload, String field) {
        if (!payload.hasNonNull(field) || payload.get(field).asText().isBlank()) {
            throw new BusinessException(422, "Missing required field: " + field);
        }
        try {
            return UUID.fromString(payload.get(field).asText());
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(422, "Invalid UUID field: " + field);
        }
    }
}
