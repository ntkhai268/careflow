package com.careflow.notification.config;

import com.careflow.common.constants.AppConstants;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Configuration
public class RabbitMqConfig {
    public static final String NOTIFICATION_QUEUE = "notification.queue.events";
    private static final String DLX = "careflow.dlx";
    private static final String DLQ = "notification.queue.events.dlq";

    @Bean
    Declarables notificationTopology() {
        TopicExchange queueExchange = new TopicExchange(AppConstants.EXCHANGE_QUEUE, true, false);
        DirectExchange dlx = new DirectExchange(DLX, true, false);
        Queue input = QueueBuilder.durable(NOTIFICATION_QUEUE)
                .withArguments(Map.of("x-dead-letter-exchange", DLX, "x-dead-letter-routing-key", DLQ)).build();
        Queue dead = QueueBuilder.durable(DLQ).build();
        return new Declarables(queueExchange, dlx, input, dead,
                BindingBuilder.bind(input).to(queueExchange).with("queue.#"),
                BindingBuilder.bind(dead).to(dlx).with(DLQ));
    }

    @Bean
    Jackson2JsonMessageConverter jacksonMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }
}
