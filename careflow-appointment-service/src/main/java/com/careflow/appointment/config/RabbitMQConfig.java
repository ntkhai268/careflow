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

    public static final String EXCHANGE_CONSULTATION = "careflow.consultation";
    public static final String ROUTING_KEY_CONSULTATION_COMPLETED = "consultation.completed";
    public static final String QUEUE_CONSULTATION_COMPLETED = "queue.appointment.consultation.completed";

    @Bean
    public TopicExchange appointmentExchange() {
        return new TopicExchange(AppConstants.EXCHANGE_APPOINTMENT);
    }

    @Bean
    public TopicExchange consultationExchange() {
        return new TopicExchange(EXCHANGE_CONSULTATION);
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
    public Queue consultationCompletedQueue() {
        return QueueBuilder.durable(QUEUE_CONSULTATION_COMPLETED).build();
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
    public Binding bindingConsultationCompleted(Queue consultationCompletedQueue,
                                                TopicExchange consultationExchange) {
        return BindingBuilder
                .bind(consultationCompletedQueue)
                .to(consultationExchange)
                .with(ROUTING_KEY_CONSULTATION_COMPLETED);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
