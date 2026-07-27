package com.careflow.appointment.config;

import com.careflow.common.constants.AppConstants;
import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String QUEUE_APPOINTMENT_CREATED = "queue.appointment.created";
    public static final String QUEUE_APPOINTMENT_CANCELLED = "queue.appointment.cancelled";

    @Bean
    public TopicExchange appointmentExchange() {
        return new TopicExchange(AppConstants.EXCHANGE_APPOINTMENT);
    }

    @Bean
    public Queue appointmentCreatedQueue() {
        return QueueBuilder.durable(QUEUE_APPOINTMENT_CREATED).build();
    }

    @Bean
    public Queue appointmentCancelledQueue() {
        return QueueBuilder.durable(QUEUE_APPOINTMENT_CANCELLED).build();
    }

    @Bean
    public Binding bindingAppointmentCreated(Queue appointmentCreatedQueue,
                                              TopicExchange appointmentExchange) {
        return BindingBuilder
                .bind(appointmentCreatedQueue)
                .to(appointmentExchange)
                .with(AppConstants.RK_APPOINTMENT_CREATED);
    }

    @Bean
    public Binding bindingAppointmentCancelled(Queue appointmentCancelledQueue,
                                                TopicExchange appointmentExchange) {
        return BindingBuilder
                .bind(appointmentCancelledQueue)
                .to(appointmentExchange)
                .with(AppConstants.RK_APPOINTMENT_CANCELLED);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
