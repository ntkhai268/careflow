package com.careflow.notification.messaging;

import com.careflow.common.event.EventEnvelope;
import com.careflow.notification.config.RabbitMqConfig;
import com.careflow.notification.service.NotificationService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class NotificationEventConsumers {
    private final NotificationService service;

    public NotificationEventConsumers(NotificationService service) {
        this.service = service;
    }

    @RabbitListener(queues = RabbitMqConfig.PATIENT_QUEUE)
    public void patient(EventEnvelope envelope) {
        service.upsertPatientRecipient(envelope);
    }

    @RabbitListener(queues = RabbitMqConfig.APPOINTMENT_QUEUE)
    public void appointment(EventEnvelope envelope) {
        service.consume(envelope);
    }

    @RabbitListener(queues = RabbitMqConfig.QUEUE_QUEUE)
    public void queue(EventEnvelope envelope) {
        service.consume(envelope);
    }

    @RabbitListener(queues = RabbitMqConfig.LAB_QUEUE)
    public void laboratory(EventEnvelope envelope) {
        service.consume(envelope);
    }

    @RabbitListener(queues = RabbitMqConfig.PRESCRIPTION_QUEUE)
    public void prescription(EventEnvelope envelope) {
        service.consume(envelope);
    }
}
