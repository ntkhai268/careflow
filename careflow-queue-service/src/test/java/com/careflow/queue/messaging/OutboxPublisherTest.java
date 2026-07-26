package com.careflow.queue.messaging;

import com.careflow.queue.domain.DeliveryStatus;
import com.careflow.queue.domain.OutboxEvent;
import com.careflow.queue.repository.OutboxEventRepository;
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
class OutboxPublisherTest {
    @Mock OutboxEventRepository outbox;
    @Mock RabbitTemplate rabbitTemplate;

    @Test
    void marksEventPublishedOnlyAfterBrokerConfirm() {
        OutboxEvent event = event();
        when(outbox.findByStatusInAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
                anyCollection(), any(), any())).thenReturn(List.of(event));
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(3);
            correlation.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbitTemplate).convertAndSend(
                anyString(), anyString(), any(JsonNode.class), any(CorrelationData.class));

        new OutboxPublisher(outbox, rabbitTemplate, 3).publishPending();

        assertThat(event.getStatus()).isEqualTo(DeliveryStatus.PUBLISHED);
        assertThat(event.getPublishedAt()).isNotNull();
    }

    @Test
    void movesPermanentlyFailingEventToDeadAfterMaxAttempts() {
        OutboxEvent event = event();
        event.setAttempts(2);
        when(outbox.findByStatusInAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
                anyCollection(), any(), any())).thenReturn(List.of(event));
        doThrow(new IllegalStateException("broker unavailable")).when(rabbitTemplate)
                .convertAndSend(anyString(), anyString(), any(JsonNode.class), any(CorrelationData.class));

        new OutboxPublisher(outbox, rabbitTemplate, 3).publishPending();

        assertThat(event.getAttempts()).isEqualTo(3);
        assertThat(event.getStatus()).isEqualTo(DeliveryStatus.DEAD);
        assertThat(event.getLastError()).contains("broker unavailable");
    }

    private OutboxEvent event() {
        OutboxEvent event = new OutboxEvent();
        event.setEventId(UUID.randomUUID());
        event.setExchangeName("queue.exchange");
        event.setRoutingKey("queue.called");
        event.setPayload(JsonNodeFactory.instance.objectNode());
        event.setStatus(DeliveryStatus.PENDING);
        event.setNextAttemptAt(Instant.now());
        event.setCreatedAt(Instant.now());
        return event;
    }
}
