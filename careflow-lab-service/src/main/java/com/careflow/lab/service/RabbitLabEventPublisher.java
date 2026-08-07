package com.careflow.lab.service;

import com.careflow.common.constants.AppConstants;
import com.careflow.common.event.EventEnvelope;
import com.careflow.lab.config.RabbitMqConfig;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class RabbitLabEventPublisher implements LabEventPublisher {
    private final RabbitTemplate rabbitTemplate;

    public RabbitLabEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @Override
    public void publishReadyForExecution(EventEnvelope envelope) {
        rabbitTemplate.convertAndSend(AppConstants.EXCHANGE_LAB,
                AppConstants.RK_LAB_ORDER_READY, envelope);
    }

    @Override
    public void publishAllRequiredResultsAvailable(EventEnvelope envelope) {
        rabbitTemplate.convertAndSend(AppConstants.EXCHANGE_LAB,
                AppConstants.RK_LAB_ALL_REQUIRED_RESULTS_AVAILABLE, envelope);
    }
}
