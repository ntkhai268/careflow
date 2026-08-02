package com.careflow.appointment.messaging;

import com.careflow.appointment.model.AppointmentOutboxEvent;
import com.careflow.appointment.repository.AppointmentOutboxRepository;
import com.careflow.common.constants.AppConstants;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
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
public class AppointmentOutboxPublisher {
    private final AppointmentOutboxRepository outbox;
    private final RabbitTemplate rabbitTemplate;
    private final int maxAttempts;

    public AppointmentOutboxPublisher(AppointmentOutboxRepository outbox, RabbitTemplate rabbitTemplate,
                                      @Value("${appointment.outbox-max-attempts:12}") int maxAttempts) {
        this.outbox = outbox;
        this.rabbitTemplate = rabbitTemplate;
        this.maxAttempts = maxAttempts;
    }

    @Scheduled(fixedDelayString = "${appointment.outbox-poll-ms:1000}")
    @Transactional
    public void publishPending() {
        List<AppointmentOutboxEvent> batch = outbox
                .findByStatusInAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
                        Set.of(AppointmentOutboxEvent.Status.PENDING, AppointmentOutboxEvent.Status.FAILED),
                        Instant.now(), PageRequest.of(0, 50));
        for (AppointmentOutboxEvent event : batch) publish(event);
    }

    private void publish(AppointmentOutboxEvent event) {
        try {
            CorrelationData correlation = new CorrelationData(event.getEventId().toString());
            rabbitTemplate.convertAndSend(AppConstants.EXCHANGE_APPOINTMENT,
                    event.getRoutingKey(), event.getPayload(), correlation);
            CorrelationData.Confirm confirm = correlation.getFuture().get(5, TimeUnit.SECONDS);
            if (!confirm.isAck()) throw new IllegalStateException("RabbitMQ NACK: " + confirm.getReason());
            event.setStatus(AppointmentOutboxEvent.Status.PUBLISHED);
            event.setPublishedAt(Instant.now());
            event.setLastError(null);
        } catch (Exception exception) {
            int attempts = event.getAttempts() + 1;
            event.setAttempts(attempts);
            event.setStatus(attempts >= maxAttempts
                    ? AppointmentOutboxEvent.Status.DEAD : AppointmentOutboxEvent.Status.FAILED);
            event.setNextAttemptAt(Instant.now().plus(
                    Math.min(300, 1L << Math.min(attempts, 8)), ChronoUnit.SECONDS));
            event.setLastError(exception.getMessage() == null
                    ? exception.getClass().getSimpleName() : exception.getMessage());
        }
        outbox.save(event);
    }
}
