package com.careflow.notification.service;

import com.careflow.common.event.EventEnvelope;
import com.careflow.common.exception.ResourceNotFoundException;
import com.careflow.notification.domain.*;
import com.careflow.notification.dto.NotificationResponse;
import com.careflow.notification.repository.NotificationRepository;
import com.careflow.notification.repository.PatientRecipientRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class NotificationServiceTest {
    private NotificationRepository notifications;
    private PatientRecipientRepository recipients;
    private SimpMessagingTemplate messaging;
    private NotificationEventService events;
    private NotificationService service;
    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        notifications = mock(NotificationRepository.class);
        recipients = mock(PatientRecipientRepository.class);
        messaging = mock(SimpMessagingTemplate.class);
        events = mock(NotificationEventService.class);
        service = new NotificationService(notifications, recipients, messaging, events);
        mapper = new ObjectMapper();
        when(notifications.save(any(Notification.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void persistsAndSendsQueueCallToEventRecipient() {
        UUID userId = UUID.randomUUID();
        UUID entryId = UUID.randomUUID();
        ObjectNode payload = mapper.createObjectNode()
                .put("recipientUserId", userId.toString())
                .put("entryId", entryId.toString())
                .put("queueNumber", "47")
                .put("roomId", "Phòng 21");

        Optional<NotificationResponse> result = service.consume(event("PatientCalled", payload));

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().type()).isEqualTo(NotificationType.QUEUE_CALLED);
        assertThat(result.orElseThrow().body()).contains("47", "Phòng 21");
        assertThat(result.orElseThrow().action().resourceId()).isEqualTo(entryId.toString());
        verify(messaging).convertAndSendToUser(userId.toString(), "/queue/notifications", result.orElseThrow());
        verify(events).delivered(any(Notification.class));
    }

    @Test
    void duplicateSourceEventDoesNotCreateSecondInboxItem() {
        UUID eventId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        ObjectNode payload = mapper.createObjectNode()
                .put("recipientUserId", userId.toString())
                .put("entryId", UUID.randomUUID().toString());
        EventEnvelope envelope = event(eventId, "QueueEntryMissed", payload);
        when(notifications.existsBySourceEventIdAndRecipientUserIdAndType(
                eventId, userId, NotificationType.QUEUE_MISSED)).thenReturn(true);

        assertThat(service.consume(envelope)).isEmpty();

        verify(notifications, never()).save(any());
        verifyNoInteractions(messaging);
    }

    @Test
    void resolvesClinicalEventRecipientFromPatientProjection() {
        UUID patientId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        PatientRecipientProjection projection = new PatientRecipientProjection();
        projection.setPatientId(patientId);
        projection.setUserId(userId);
        when(recipients.findById(patientId)).thenReturn(Optional.of(projection));
        ObjectNode payload = mapper.createObjectNode()
                .put("patientId", patientId.toString())
                .put("orderId", UUID.randomUUID().toString())
                .put("consultationId", UUID.randomUUID().toString());

        NotificationResponse response = service.consume(event("AllRequiredResultsAvailable", payload))
                .orElseThrow();

        assertThat(response.type()).isEqualTo(NotificationType.RETURN_FOR_REVIEW);
        verify(messaging).convertAndSendToUser(eq(userId.toString()), eq("/queue/notifications"), any());
    }

    @Test
    void missingPatientProjectionFailsSoBrokerCanRetryAndDeadLetter() {
        UUID patientId = UUID.randomUUID();
        when(recipients.findById(patientId)).thenReturn(Optional.empty());
        ObjectNode payload = mapper.createObjectNode()
                .put("patientId", patientId.toString())
                .put("prescriptionId", UUID.randomUUID().toString());

        assertThatThrownBy(() -> service.consume(event("PrescriptionIssued", payload)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(patientId.toString());
    }

    @Test
    void patientProfileEventUpsertsRecipientProjection() {
        UUID patientId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        ObjectNode payload = mapper.createObjectNode()
                .put("patientId", patientId.toString())
                .put("userId", userId.toString());
        when(recipients.findById(patientId)).thenReturn(Optional.empty());

        service.upsertPatientRecipient(event("PatientProfileCreated", payload));

        ArgumentCaptor<PatientRecipientProjection> captor = ArgumentCaptor.forClass(PatientRecipientProjection.class);
        verify(recipients).save(captor.capture());
        assertThat(captor.getValue().getPatientId()).isEqualTo(patientId);
        assertThat(captor.getValue().getUserId()).isEqualTo(userId);
    }

    @Test
    void markReadIsOwnedByAuthenticatedUserAndIdempotent() {
        UUID userId = UUID.randomUUID();
        UUID notificationId = UUID.randomUUID();
        Notification notification = notification(notificationId, userId, NotificationStatus.DELIVERED);
        when(notifications.findByIdAndRecipientUserId(notificationId, userId))
                .thenReturn(Optional.of(notification));

        NotificationResponse first = service.markRead(userId, notificationId);
        NotificationResponse second = service.markRead(userId, notificationId);

        assertThat(first.status()).isEqualTo(NotificationStatus.READ);
        assertThat(first.readAt()).isNotNull();
        assertThat(second.readAt()).isEqualTo(first.readAt());
        verify(notifications, times(1)).save(notification);
        verify(events, times(1)).read(notification);
    }

    @Test
    void anotherUserCannotReadNotification() {
        UUID notificationId = UUID.randomUUID();
        UUID anotherUser = UUID.randomUUID();
        when(notifications.findByIdAndRecipientUserId(notificationId, anotherUser))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.markRead(anotherUser, notificationId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private EventEnvelope event(String type, ObjectNode payload) {
        return event(UUID.randomUUID(), type, payload);
    }

    private EventEnvelope event(UUID id, String type, ObjectNode payload) {
        return new EventEnvelope(id, type, 1, UUID.randomUUID(), 1, Instant.now(),
                "test-service", id.toString(), payload);
    }

    private Notification notification(UUID id, UUID userId, NotificationStatus status) {
        Notification result = new Notification();
        result.setId(id);
        result.setRecipientUserId(userId);
        result.setSourceEventId(UUID.randomUUID());
        result.setSourceEventType("PatientCalled");
        result.setType(NotificationType.QUEUE_CALLED);
        result.setTitle("Đã đến lượt");
        result.setBody("Mời vào phòng khám");
        result.setStatus(status);
        result.setCreatedAt(Instant.now());
        return result;
    }
}
