package com.careflow.appointment.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE_NAME = "careflow.appointment";
    public static final String ROUTING_KEY_CREATED = "appointment.created";
    public static final String ROUTING_KEY_CANCELLED = "appointment.cancelled";
    public static final String QUEUE_APPOINTMENT_CREATED = "queue.appointment.created";

    @Bean
    public TopicExchange appointmentExchange() {
        return new TopicExchange(EXCHANGE_NAME);
    }

    @Bean
    public Queue appointmentCreatedQueue() {
        return QueueBuilder.durable(QUEUE_APPOINTMENT_CREATED).build();
    }

    @Bean
    public Binding bindingAppointmentCreated(Queue appointmentCreatedQueue,
                                              TopicExchange appointmentExchange) {
        return BindingBuilder
                .bind(appointmentCreatedQueue)
                .to(appointmentExchange)
                .with(ROUTING_KEY_CREATED);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
