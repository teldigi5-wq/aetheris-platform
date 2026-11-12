package io.aetheris.users.events;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitEventConfig {
    @Bean
    TopicExchange aetherisEventsExchange() {
        return new TopicExchange(UserEventPublisher.EXCHANGE, true, false);
    }

    @Bean
    JacksonJsonMessageConverter rabbitMessageConverter() {
        return new JacksonJsonMessageConverter();
    }
}
