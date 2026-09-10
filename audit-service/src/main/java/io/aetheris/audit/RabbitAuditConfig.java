package io.aetheris.audit;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitAuditConfig {
    public static final String EXCHANGE = "aetheris.events";
    public static final String USER_AUDIT_QUEUE = "aetheris.audit.user-events";

    @Bean
    TopicExchange aetherisEventsExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean
    Queue userAuditQueue() {
        return new Queue(USER_AUDIT_QUEUE, true);
    }

    @Bean
    Binding userAuditBinding(Queue userAuditQueue, TopicExchange aetherisEventsExchange) {
        return BindingBuilder.bind(userAuditQueue).to(aetherisEventsExchange).with("user.*");
    }

    @Bean
    Jackson2JsonMessageConverter rabbitMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
