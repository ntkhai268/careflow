package com.careflow.notification.config;

import com.careflow.common.constants.AppConstants;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Configuration
public class RabbitMqConfig {
    public static final String PATIENT_QUEUE = "notification.patient.events";
    public static final String APPOINTMENT_QUEUE = "notification.appointment.events";
    public static final String QUEUE_QUEUE = "notification.queue.events";
    public static final String LAB_QUEUE = "notification.lab.events";
    public static final String PRESCRIPTION_QUEUE = "notification.prescription.events";
    public static final String PATIENT_EXCHANGE = "patient.exchange";
    private static final String DLX = "careflow.dlx";

    @Bean
    Declarables notificationTopology() {
        TopicExchange patient = new TopicExchange(PATIENT_EXCHANGE, true, false);
        TopicExchange appointment = new TopicExchange(AppConstants.EXCHANGE_APPOINTMENT, true, false);
        TopicExchange queue = new TopicExchange(AppConstants.EXCHANGE_QUEUE, true, false);
        TopicExchange lab = new TopicExchange(AppConstants.EXCHANGE_LAB, true, false);
        TopicExchange prescription = new TopicExchange(AppConstants.EXCHANGE_PRESCRIPTION, true, false);
        TopicExchange notification = new TopicExchange(AppConstants.EXCHANGE_NOTIFICATION, true, false);
        DirectExchange dlx = new DirectExchange(DLX, true, false);

        List<Declarable> declarations = new ArrayList<>(List.of(
                patient, appointment, queue, lab, prescription, notification, dlx));
        bind(declarations, PATIENT_QUEUE, patient, "patient.profile.created");
        bind(declarations, APPOINTMENT_QUEUE, appointment, "appointment.#");
        bind(declarations, QUEUE_QUEUE, queue, "queue.#");
        bind(declarations, LAB_QUEUE, lab, "lab.#");
        bind(declarations, PRESCRIPTION_QUEUE, prescription, "prescription.#");
        return new Declarables(declarations);
    }

    private void bind(List<Declarable> declarations, String name, TopicExchange exchange, String routingKey) {
        String dlqName = name + ".dlq";
        Queue input = QueueBuilder.durable(name)
                .withArguments(Map.of("x-dead-letter-exchange", DLX,
                        "x-dead-letter-routing-key", dlqName))
                .build();
        Queue dead = QueueBuilder.durable(dlqName).build();
        declarations.add(input);
        declarations.add(dead);
        declarations.add(BindingBuilder.bind(input).to(exchange).with(routingKey));
        declarations.add(BindingBuilder.bind(dead).to(new DirectExchange(DLX)).with(dlqName));
    }

    @Bean
    Jackson2JsonMessageConverter jacksonMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    @Bean
    RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, Jackson2JsonMessageConverter converter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(converter);
        // Consumer queues are owned by their services and may not exist yet in the MVP.
        // Publisher confirms still prove the durable exchange accepted the outbox event.
        template.setMandatory(false);
        return template;
    }
}
