package com.careflow.queue.config;

import com.careflow.common.constants.AppConstants;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Configuration
public class RabbitMqConfig {
    public static final String APPOINTMENT_QUEUE = "queue.appointment.events";
    public static final String APPOINTMENT_DLQ = "queue.appointment.events.dlq";
    public static final String PRESCRIPTION_QUEUE = "queue.prescription.events";
    public static final String PRESCRIPTION_DLQ = "queue.prescription.events.dlq";
    public static final String LAB_RESULTS_QUEUE = "queue.lab.result-events";
    public static final String LAB_RESULTS_DLQ = "queue.lab.result-events.dlq";
    public static final String LAB_READY_QUEUE = "queue.lab.ready-events";
    public static final String LAB_READY_DLQ = "queue.lab.ready-events.dlq";
    private static final String NOTIFICATION_QUEUE = "notification.queue.events";
    private static final String NOTIFICATION_DLQ = "notification.queue.events.dlq";
    private static final String DLX = "careflow.dlx";

    @Bean
    Declarables queueTopology() {
        TopicExchange appointment = new TopicExchange(AppConstants.EXCHANGE_APPOINTMENT, true, false);
        TopicExchange queue = new TopicExchange(AppConstants.EXCHANGE_QUEUE, true, false);
        TopicExchange prescription = new TopicExchange(AppConstants.EXCHANGE_PRESCRIPTION, true, false);
        TopicExchange lab = new TopicExchange(AppConstants.EXCHANGE_LAB, true, false);
        DirectExchange dlx = new DirectExchange(DLX, true, false);
        Queue input = QueueBuilder.durable(APPOINTMENT_QUEUE)
                .withArguments(Map.of("x-dead-letter-exchange", DLX,
                        "x-dead-letter-routing-key", APPOINTMENT_DLQ)).build();
        Queue dead = QueueBuilder.durable(APPOINTMENT_DLQ).build();
        Queue prescriptionInput = QueueBuilder.durable(PRESCRIPTION_QUEUE)
                .withArguments(Map.of("x-dead-letter-exchange", DLX,
                        "x-dead-letter-routing-key", PRESCRIPTION_DLQ)).build();
        Queue prescriptionDead = QueueBuilder.durable(PRESCRIPTION_DLQ).build();
        Queue labResultsInput = QueueBuilder.durable(LAB_RESULTS_QUEUE)
                .withArguments(Map.of("x-dead-letter-exchange", DLX,
                        "x-dead-letter-routing-key", LAB_RESULTS_DLQ)).build();
        Queue labResultsDead = QueueBuilder.durable(LAB_RESULTS_DLQ).build();
        Queue labReadyInput = QueueBuilder.durable(LAB_READY_QUEUE)
                .withArguments(Map.of("x-dead-letter-exchange", DLX,
                        "x-dead-letter-routing-key", LAB_READY_DLQ)).build();
        Queue labReadyDead = QueueBuilder.durable(LAB_READY_DLQ).build();
        Queue notification = QueueBuilder.durable(NOTIFICATION_QUEUE)
                .withArguments(Map.of("x-dead-letter-exchange", DLX,
                        "x-dead-letter-routing-key", NOTIFICATION_DLQ)).build();
        Queue notificationDead = QueueBuilder.durable(NOTIFICATION_DLQ).build();
        return new Declarables(appointment, queue, prescription, lab, dlx, input, dead,
                prescriptionInput, prescriptionDead, labResultsInput, labResultsDead,
                labReadyInput, labReadyDead, notification, notificationDead,
                BindingBuilder.bind(input).to(appointment).with("appointment.*"),
                BindingBuilder.bind(dead).to(dlx).with(APPOINTMENT_DLQ),
                BindingBuilder.bind(prescriptionInput).to(prescription).with("prescription.*"),
                BindingBuilder.bind(prescriptionDead).to(dlx).with(PRESCRIPTION_DLQ),
                BindingBuilder.bind(labResultsInput).to(lab)
                        .with(AppConstants.RK_LAB_ALL_REQUIRED_RESULTS_AVAILABLE),
                BindingBuilder.bind(labResultsDead).to(dlx).with(LAB_RESULTS_DLQ),
                BindingBuilder.bind(labReadyInput).to(lab).with(AppConstants.RK_LAB_ORDER_READY),
                BindingBuilder.bind(labReadyDead).to(dlx).with(LAB_READY_DLQ),
                BindingBuilder.bind(notification).to(queue).with("queue.#"),
                BindingBuilder.bind(notificationDead).to(dlx).with(NOTIFICATION_DLQ));
    }

    @Bean
    Jackson2JsonMessageConverter jacksonMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    @Bean
    RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, Jackson2JsonMessageConverter converter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(converter);
        template.setMandatory(true);
        return template;
    }
}
