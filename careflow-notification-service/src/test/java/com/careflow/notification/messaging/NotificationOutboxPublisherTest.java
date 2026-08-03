package com.careflow.notification.messaging;

import com.careflow.notification.domain.OutboxEvent;
import com.careflow.notification.domain.OutboxStatus;
import com.careflow.notification.repository.OutboxEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class NotificationOutboxPublisherTest {
    @Test
    void marksEventPublishedAfterBrokerConfirm() {
        OutboxEventRepository repository = mock(OutboxEventRepository.class);
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        OutboxEvent event = pendingEvent();
        when(repository.findByStatusInAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
                anySet(), any(), any())).thenReturn(List.of(event));
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(3);
            correlation.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Object.class),
                any(CorrelationData.class));

        new NotificationOutboxPublisher(repository, rabbitTemplate, 3).publishPending();

        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
        assertThat(event.getPublishedAt()).isNotNull();
        verify(repository).save(event);
    }

    @Test
    void retriesFailedPublishWithBackoff() {
        OutboxEventRepository repository = mock(OutboxEventRepository.class);
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        OutboxEvent event = pendingEvent();
        when(repository.findByStatusInAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
                anySet(), any(), any())).thenReturn(List.of(event));
        doThrow(new AmqpException("broker unavailable")).when(rabbitTemplate)
                .convertAndSend(anyString(), anyString(), any(Object.class), any(CorrelationData.class));

        new NotificationOutboxPublisher(repository, rabbitTemplate, 3).publishPending();

        assertThat(event.getStatus()).isEqualTo(OutboxStatus.FAILED);
        assertThat(event.getAttempts()).isEqualTo(1);
        assertThat(event.getLastError()).contains("broker unavailable");
        assertThat(event.getNextAttemptAt()).isAfter(Instant.now());
    }

    private OutboxEvent pendingEvent() {
        OutboxEvent event = new OutboxEvent();
        event.setEventId(UUID.randomUUID());
        event.setAggregateId(UUID.randomUUID());
        event.setExchangeName("notification.exchange");
        event.setRoutingKey("notification.delivered");
        event.setPayload(new ObjectMapper().createObjectNode().put("eventType", "NotificationDelivered"));
        event.setStatus(OutboxStatus.PENDING);
        event.setAttempts(0);
        event.setNextAttemptAt(Instant.now());
        event.setCreatedAt(Instant.now());
        return event;
    }
}
