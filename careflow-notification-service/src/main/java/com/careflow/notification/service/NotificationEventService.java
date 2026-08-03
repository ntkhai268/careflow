package com.careflow.notification.service;

import com.careflow.common.constants.AppConstants;
import com.careflow.common.event.EventEnvelope;
import com.careflow.notification.domain.Notification;
import com.careflow.notification.domain.OutboxEvent;
import com.careflow.notification.domain.OutboxStatus;
import com.careflow.notification.repository.OutboxEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class NotificationEventService {
    private final OutboxEventRepository outbox;
    private final ObjectMapper objectMapper;

    public NotificationEventService(OutboxEventRepository outbox, ObjectMapper objectMapper) {
        this.outbox = outbox;
        this.objectMapper = objectMapper;
    }

    public void delivered(Notification notification) {
        append(notification, "NotificationDelivered", "notification.delivered", Map.of(
                "notificationId", notification.getId(),
                "recipientUserId", notification.getRecipientUserId(),
                "type", notification.getType(),
                "channel", "IN_APP",
                "deliveredAt", notification.getCreatedAt()));
    }

    public void read(Notification notification) {
        append(notification, "NotificationRead", "notification.read", Map.of(
                "notificationId", notification.getId(),
                "recipientUserId", notification.getRecipientUserId(),
                "type", notification.getType(),
                "readAt", notification.getReadAt()));
    }

    private void append(Notification notification, String eventType, String routingKey, Map<String, Object> payload) {
        UUID eventId = UUID.randomUUID();
        Instant now = Instant.now();
        Map<String, Object> orderedPayload = new LinkedHashMap<>(payload);
        EventEnvelope envelope = new EventEnvelope(eventId, eventType, 1, notification.getId(), 1,
                now, "notification-service", notification.getSourceEventId().toString(),
                objectMapper.valueToTree(orderedPayload));
        OutboxEvent event = new OutboxEvent();
        event.setEventId(eventId);
        event.setAggregateId(notification.getId());
        event.setEventType(eventType);
        event.setExchangeName(AppConstants.EXCHANGE_NOTIFICATION);
        event.setRoutingKey(routingKey);
        event.setPayload(objectMapper.valueToTree(envelope));
        event.setStatus(OutboxStatus.PENDING);
        event.setAttempts(0);
        event.setOccurredAt(now);
        event.setNextAttemptAt(now);
        event.setCreatedAt(now);
        outbox.save(event);
    }
}
