package com.careflow.queue.messaging;

import com.careflow.queue.domain.DeliveryStatus;
import com.careflow.queue.domain.OutboxEvent;
import com.careflow.queue.repository.OutboxEventRepository;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Component
public class OutboxPublisher {
    private final OutboxEventRepository outbox;
    private final RabbitTemplate rabbitTemplate;

    public OutboxPublisher(OutboxEventRepository outbox, RabbitTemplate rabbitTemplate) {
        this.outbox = outbox;
        this.rabbitTemplate = rabbitTemplate;
    }

    @Scheduled(fixedDelayString = "${queue.outbox-poll-ms:1000}")
    @Transactional
    public void publishPending() {
        List<OutboxEvent> batch = outbox.findByStatusInAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
                Set.of(DeliveryStatus.PENDING, DeliveryStatus.FAILED), Instant.now(), PageRequest.of(0, 50));
        for (OutboxEvent event : batch) publish(event);
    }

    private void publish(OutboxEvent event) {
        CorrelationData correlation = new CorrelationData(event.getEventId().toString());
        try {
            rabbitTemplate.convertAndSend(event.getExchangeName(), event.getRoutingKey(), event.getPayload(), correlation);
            CorrelationData.Confirm confirm = correlation.getFuture().get(5, TimeUnit.SECONDS);
            if (!confirm.isAck()) throw new IllegalStateException("RabbitMQ NACK: " + confirm.getReason());
            event.setStatus(DeliveryStatus.PUBLISHED);
            event.setPublishedAt(Instant.now());
            event.setLastError(null);
        } catch (Exception exception) {
            int attempts = event.getAttempts() + 1;
            event.setAttempts(attempts);
            event.setStatus(DeliveryStatus.FAILED);
            long delay = Math.min(300, 1L << Math.min(attempts, 8));
            event.setNextAttemptAt(Instant.now().plus(delay, ChronoUnit.SECONDS));
            event.setLastError(exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage());
        }
        outbox.save(event);
    }
}
