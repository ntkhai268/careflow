package com.careflow.lab.service;

import com.careflow.common.constants.AppConstants;
import com.careflow.common.event.EventEnvelope;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

@Component
public class RabbitLabEventPublisher implements LabEventPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final LabEventOutbox outbox;

    public RabbitLabEventPublisher(RabbitTemplate rabbitTemplate) {
        this(rabbitTemplate, null);
    }

    @Autowired
    public RabbitLabEventPublisher(RabbitTemplate rabbitTemplate, @Nullable LabEventOutbox outbox) {
        this.rabbitTemplate = rabbitTemplate;
        this.outbox = outbox;
    }

    @Override
    public void publishReadyForExecution(EventEnvelope envelope) {
        if (outbox != null) {
            outbox.append(
                    envelope.aggregateId(),
                    envelope.eventType(),
                    AppConstants.RK_LAB_ORDER_READY,
                    envelope);
        } else {
            rabbitTemplate.convertAndSend(
                    AppConstants.EXCHANGE_LAB,
                    AppConstants.RK_LAB_ORDER_READY,
                    envelope);
        }
    }

    @Override
    public void publishAllRequiredResultsAvailable(EventEnvelope envelope) {
        if (outbox != null) {
            outbox.append(
                    envelope.aggregateId(),
                    envelope.eventType(),
                    AppConstants.RK_LAB_ALL_REQUIRED_RESULTS_AVAILABLE,
                    envelope);
        } else {
            rabbitTemplate.convertAndSend(
                    AppConstants.EXCHANGE_LAB,
                    AppConstants.RK_LAB_ALL_REQUIRED_RESULTS_AVAILABLE,
                    envelope);
        }
    }
}
