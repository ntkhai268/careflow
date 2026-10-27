package com.careflow.consultation.config;

import com.careflow.common.constants.AppConstants;
import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String LAB_RESULTS_QUEUE = "consultation.lab.result-events";

    @Bean
    public TopicExchange consultationExchange() {
        return new TopicExchange(AppConstants.EXCHANGE_CONSULTATION);
    }

    @Bean
    public TopicExchange labExchange() {
        return new TopicExchange(AppConstants.EXCHANGE_LAB);
    }

    @Bean
    public Queue consultationCompletedQueue() {
        return new Queue("consultation.completed.queue", true);
    }

    @Bean
    public Queue consultationLabResultsQueue() {
        return new Queue(LAB_RESULTS_QUEUE, true);
    }

    @Bean
    public Binding consultationCompletedBinding(Queue consultationCompletedQueue, TopicExchange consultationExchange) {
        return BindingBuilder.bind(consultationCompletedQueue)
                .to(consultationExchange)
                .with(AppConstants.RK_CONSULTATION_COMPLETED);
    }

    @Bean
    public Binding consultationLabResultsBinding(Queue consultationLabResultsQueue, TopicExchange labExchange) {
        return BindingBuilder.bind(consultationLabResultsQueue)
                .to(labExchange)
                .with(AppConstants.RK_LAB_ALL_REQUIRED_RESULTS_AVAILABLE);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
