package com.careflow.notification.service;

import com.careflow.common.event.EventEnvelope;
import com.careflow.common.exception.ResourceNotFoundException;
import com.careflow.notification.domain.*;
import com.careflow.notification.dto.NotificationActionResponse;
import com.careflow.notification.dto.NotificationResponse;
import com.careflow.notification.repository.NotificationRepository;
import com.careflow.notification.repository.PatientRecipientRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class NotificationService {
    private static final String USER_DESTINATION = "/queue/notifications";
    private final NotificationRepository notifications;
    private final PatientRecipientRepository recipients;
    private final SimpMessagingTemplate messaging;
    private final NotificationEventService events;
    private final PushDeliveryService pushDeliveries;

    public NotificationService(NotificationRepository notifications, PatientRecipientRepository recipients,
                               SimpMessagingTemplate messaging, NotificationEventService events,
                               PushDeliveryService pushDeliveries) {
        this.notifications = notifications;
        this.recipients = recipients;
        this.messaging = messaging;
        this.events = events;
        this.pushDeliveries = pushDeliveries;
    }

    @Transactional
    public void upsertPatientRecipient(EventEnvelope envelope) {
        requireEnvelope(envelope);
        if (!"PatientProfileCreated".equals(envelope.eventType())) return;
        UUID patientId = requiredUuid(envelope.payload(), "patientId");
        UUID userId = requiredUuid(envelope.payload(), "userId");
        PatientRecipientProjection projection = recipients.findById(patientId)
                .orElseGet(PatientRecipientProjection::new);
        projection.setPatientId(patientId);
        projection.setUserId(userId);
        projection.setUpdatedAt(envelope.occurredAt() == null ? Instant.now() : envelope.occurredAt());
        recipients.save(projection);
    }

    @Transactional
    public Optional<NotificationResponse> consume(EventEnvelope envelope) {
        requireEnvelope(envelope);
        Template template = template(envelope.eventType(), envelope.payload());
        if (template == null) return Optional.empty();
        learnRecipientMapping(envelope.payload(), envelope.occurredAt());
        UUID recipientUserId = resolveRecipient(envelope.payload());
        if (notifications.existsBySourceEventIdAndRecipientUserIdAndType(
                envelope.eventId(), recipientUserId, template.type())) {
            return Optional.empty();
        }

        Notification notification = new Notification();
        notification.setId(UUID.randomUUID());
        notification.setRecipientUserId(recipientUserId);
        notification.setSourceEventId(envelope.eventId());
        notification.setSourceEventType(envelope.eventType());
        notification.setType(template.type());
        notification.setTitle(template.title());
        notification.setBody(template.body());
        notification.setActionType(template.actionType());
        notification.setResourceId(template.resourceId());
        notification.setStatus(NotificationStatus.DELIVERED);
        notification.setCreatedAt(envelope.occurredAt() == null ? Instant.now() : envelope.occurredAt());
        Notification saved = notifications.save(notification);
        events.delivered(saved);
        pushDeliveries.enqueue(saved);
        NotificationResponse response = toResponse(saved);
        sendAfterCommit(recipientUserId, response);
        return Optional.of(response);
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> inbox(UUID userId, NotificationStatus status, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        PageRequest page = PageRequest.of(0, safeLimit);
        List<Notification> result = status == null
                ? notifications.findByRecipientUserIdOrderByCreatedAtDesc(userId, page)
                : notifications.findByRecipientUserIdAndStatusOrderByCreatedAtDesc(userId, status, page);
        return result.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public long unreadCount(UUID userId) {
        return notifications.countByRecipientUserIdAndStatus(userId, NotificationStatus.DELIVERED);
    }

    @Transactional
    public NotificationResponse markRead(UUID userId, UUID notificationId) {
        Notification notification = notifications.findByIdAndRecipientUserId(notificationId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification", "id", notificationId));
        if (notification.getStatus() == NotificationStatus.DELIVERED) {
            notification.setStatus(NotificationStatus.READ);
            notification.setReadAt(Instant.now());
            notifications.save(notification);
            events.read(notification);
        }
        return toResponse(notification);
    }

    @Transactional
    public int markAllRead(UUID userId) {
        List<Notification> unread = notifications.findByRecipientUserIdAndStatusOrderByCreatedAtAsc(
                userId, NotificationStatus.DELIVERED);
        Instant readAt = Instant.now();
        for (Notification notification : unread) {
            notification.setStatus(NotificationStatus.READ);
            notification.setReadAt(readAt);
            notifications.save(notification);
            events.read(notification);
        }
        return unread.size();
    }

    private UUID resolveRecipient(JsonNode payload) {
        UUID direct = optionalUuid(payload, "recipientUserId");
        if (direct == null) direct = optionalUuid(payload, "userId");
        if (direct != null) return direct;
        UUID patientId = requiredUuid(payload, "patientId");
        return recipients.findById(patientId)
                .map(PatientRecipientProjection::getUserId)
                .orElseThrow(() -> new IllegalStateException(
                        "Chưa có ánh xạ user cho patientId " + patientId));
    }

    private Template template(String eventType, JsonNode payload) {
        String queueNumber = text(payload, "queueNumber", "");
        String room = text(payload, "roomId", text(payload, "servicePointId", "điểm phục vụ"));
        return switch (eventType) {
            case "AppointmentConfirmed" -> new Template(NotificationType.APPOINTMENT_CONFIRMED,
                    "Đặt khám thành công", "Lịch khám của bạn đã được xác nhận.",
                    "OPEN_APPOINTMENT", id(payload, "appointmentId"));
            case "VisitTicketIssued" -> new Template(NotificationType.VISIT_TICKET_ISSUED,
                    "Đã có phiếu khám", "Phiếu khám " + queueNumber + " đã sẵn sàng.",
                    "OPEN_TICKET", firstId(payload, "entryId", "queueEntryId"));
            case "PatientCheckedIn" -> new Template(NotificationType.CHECK_IN_SUCCESS,
                    "Check-in thành công", "Bạn đã vào danh sách chờ tại " + room + ".",
                    "OPEN_QUEUE", firstId(payload, "entryId", "queueEntryId"));
            case "QueueNearTurn" -> new Template(NotificationType.QUEUE_NEAR_TURN,
                    "Sắp đến lượt", "Vui lòng chuẩn bị tại " + room + ".",
                    "OPEN_QUEUE", firstId(payload, "entryId", "queueEntryId"));
            case "PatientCalled" -> new Template(NotificationType.QUEUE_CALLED,
                    "Đã đến lượt", "Mời số " + queueNumber + " vào " + room + ".",
                    "OPEN_QUEUE", firstId(payload, "entryId", "queueEntryId"));
            case "QueueEntryMissed" -> new Template(NotificationType.QUEUE_MISSED,
                    "Bạn đã lỡ lượt", "Vui lòng theo dõi lượt được xếp lại hoặc liên hệ nhân viên.",
                    "OPEN_QUEUE", firstId(payload, "entryId", "queueEntryId"));
            case "LabOrderCreated" -> new Template(NotificationType.LAB_ORDER_CREATED,
                    "Có chỉ định cận lâm sàng", "Bác sĩ đã tạo chỉ định mới cho bạn.",
                    "OPEN_LAB_ORDER", id(payload, "orderId"));
            case "LabOrderReadyForExecution" -> new Template(NotificationType.LAB_READY,
                    "Chỉ định đã sẵn sàng", "Vui lòng đến điểm cận lâm sàng được hướng dẫn.",
                    "OPEN_LAB_ORDER", id(payload, "orderId"));
            case "LabResultAvailable" -> new Template(NotificationType.LAB_RESULT_AVAILABLE,
                    "Đã có kết quả", "Một kết quả cận lâm sàng đã được phát hành.",
                    "OPEN_LAB_RESULT", id(payload, "orderId"));
            case "AllRequiredResultsAvailable" -> new Template(NotificationType.RETURN_FOR_REVIEW,
                    "Kết quả đã sẵn sàng", "Vui lòng quay lại phòng khám và chờ bác sĩ gọi đọc kết quả.",
                    "OPEN_RESULT_REVIEW", id(payload, "consultationId"));
            case "PrescriptionIssued" -> new Template(NotificationType.PRESCRIPTION_AVAILABLE,
                    "Đã có toa thuốc", "Toa thuốc sau khám của bạn đã sẵn sàng.",
                    "OPEN_PRESCRIPTION", id(payload, "prescriptionId"));
            case "FollowUpScheduled" -> new Template(NotificationType.FOLLOW_UP_SCHEDULED,
                    "Đã hẹn tái khám", "Bác sĩ đã tạo lịch tái khám cho bạn.",
                    "OPEN_APPOINTMENT", firstId(payload, "appointmentId", "followUpAppointmentId"));
            default -> null;
        };
    }

    private void sendAfterCommit(UUID userId, NotificationResponse response) {
        Runnable send = () -> messaging.convertAndSendToUser(userId.toString(), USER_DESTINATION, response);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { send.run(); }
            });
        } else {
            send.run();
        }
    }

    private void learnRecipientMapping(JsonNode payload, Instant occurredAt) {
        UUID patientId = optionalUuid(payload, "patientId");
        UUID userId = optionalUuid(payload, "recipientUserId");
        if (userId == null) userId = optionalUuid(payload, "userId");
        if (patientId == null || userId == null) return;
        PatientRecipientProjection projection = recipients.findById(patientId)
                .orElseGet(PatientRecipientProjection::new);
        projection.setPatientId(patientId);
        projection.setUserId(userId);
        projection.setUpdatedAt(occurredAt == null ? Instant.now() : occurredAt);
        recipients.save(projection);
    }

    private NotificationResponse toResponse(Notification value) {
        NotificationActionResponse action = value.getActionType() == null ? null
                : new NotificationActionResponse(value.getActionType(), value.getResourceId());
        return new NotificationResponse(value.getId(), value.getType(), value.getTitle(), value.getBody(), action,
                value.getStatus(), value.getCreatedAt(), value.getReadAt());
    }

    private void requireEnvelope(EventEnvelope envelope) {
        if (envelope == null || envelope.eventId() == null || envelope.eventType() == null
                || envelope.payload() == null) {
            throw new IllegalArgumentException("Event envelope không hợp lệ");
        }
    }

    private UUID requiredUuid(JsonNode payload, String field) {
        UUID value = optionalUuid(payload, field);
        if (value == null) throw new IllegalArgumentException(field + " không được để trống");
        return value;
    }

    private UUID optionalUuid(JsonNode payload, String field) {
        if (!payload.hasNonNull(field) || payload.get(field).asText().isBlank()) return null;
        return UUID.fromString(payload.get(field).asText());
    }

    private String id(JsonNode payload, String field) {
        return payload.hasNonNull(field) ? payload.get(field).asText() : null;
    }

    private String firstId(JsonNode payload, String first, String second) {
        String result = id(payload, first);
        return result == null ? id(payload, second) : result;
    }

    private String text(JsonNode payload, String field, String fallback) {
        return payload.hasNonNull(field) && !payload.get(field).asText().isBlank()
                ? payload.get(field).asText() : fallback;
    }

    private record Template(NotificationType type, String title, String body,
                            String actionType, String resourceId) {
    }
}
