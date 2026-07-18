package com.careflow.notification.messaging;

import com.careflow.common.event.EventEnvelope;
import com.careflow.notification.config.RabbitMqConfig;
import com.careflow.notification.dto.NotificationMessage;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class QueueEventConsumer {
    private final SimpMessagingTemplate messaging;

    public QueueEventConsumer(SimpMessagingTemplate messaging) {
        this.messaging = messaging;
    }

    @RabbitListener(queues = RabbitMqConfig.NOTIFICATION_QUEUE)
    public void consume(EventEnvelope envelope) {
        if (envelope == null || envelope.eventId() == null || envelope.payload() == null) {
            throw new IllegalArgumentException("Queue event envelope không hợp lệ");
        }
        JsonNode payload = envelope.payload();
        UUID entryId = uuid(payload, "queueEntryId");
        UUID departmentId = uuid(payload, "departmentId");
        NotificationMessage message = new NotificationMessage(envelope.eventId(), envelope.eventType(), entryId,
                departmentId, text(payload, "queueNumber"), text(payload, "roomCode"),
                payload.hasNonNull("estimatedWaitMinutes") ? payload.get("estimatedWaitMinutes").asInt() : null,
                envelope.occurredAt());

        UUID recipient = uuid(payload, "recipientUserId");
        if (recipient != null) messaging.convertAndSendToUser(recipient.toString(), "/queue/notifications", message);
        if (departmentId != null) messaging.convertAndSend("/topic/queues/departments/" + departmentId, message);
    }

    private UUID uuid(JsonNode node, String field) {
        if (!node.hasNonNull(field) || node.get(field).asText().isBlank()) return null;
        return UUID.fromString(node.get(field).asText());
    }

    private String text(JsonNode node, String field) {
        return node.hasNonNull(field) ? node.get(field).asText() : null;
    }
}
