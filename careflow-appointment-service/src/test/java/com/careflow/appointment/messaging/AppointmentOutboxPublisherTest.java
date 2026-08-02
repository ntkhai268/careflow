package com.careflow.appointment.messaging;

import com.careflow.appointment.model.AppointmentOutboxEvent;
import com.careflow.appointment.repository.AppointmentOutboxRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AppointmentOutboxPublisherTest {
    @Mock AppointmentOutboxRepository outbox;
    @Mock RabbitTemplate rabbitTemplate;

    @Test
    void marksEventPublishedOnlyAfterBrokerConfirm() {
        AppointmentOutboxEvent event = event();
        when(outbox.findByStatusInAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
                anyCollection(), any(), any())).thenReturn(List.of(event));
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(3);
            correlation.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbitTemplate).convertAndSend(
                anyString(), anyString(), any(JsonNode.class), any(CorrelationData.class));

        new AppointmentOutboxPublisher(outbox, rabbitTemplate, 3).publishPending();

        assertThat(event.getStatus()).isEqualTo(AppointmentOutboxEvent.Status.PUBLISHED);
        assertThat(event.getPublishedAt()).isNotNull();
    }

    @Test
    void movesPermanentlyFailingEventToDead() {
        AppointmentOutboxEvent event = event();
        event.setAttempts(2);
        when(outbox.findByStatusInAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
                anyCollection(), any(), any())).thenReturn(List.of(event));
        doThrow(new IllegalStateException("broker unavailable")).when(rabbitTemplate)
                .convertAndSend(anyString(), anyString(), any(JsonNode.class), any(CorrelationData.class));

        new AppointmentOutboxPublisher(outbox, rabbitTemplate, 3).publishPending();

        assertThat(event.getStatus()).isEqualTo(AppointmentOutboxEvent.Status.DEAD);
        assertThat(event.getAttempts()).isEqualTo(3);
    }

    private AppointmentOutboxEvent event() {
        AppointmentOutboxEvent event = new AppointmentOutboxEvent();
        event.setEventId(UUID.randomUUID());
        event.setRoutingKey("appointment.confirmed");
        event.setPayload(JsonNodeFactory.instance.objectNode());
        event.setStatus(AppointmentOutboxEvent.Status.PENDING);
        event.setNextAttemptAt(Instant.now());
        event.setCreatedAt(Instant.now());
        return event;
    }
}
