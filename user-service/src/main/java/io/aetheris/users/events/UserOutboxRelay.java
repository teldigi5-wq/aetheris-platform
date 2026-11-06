package io.aetheris.users.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.concurrent.TimeUnit;

/**
 * Bounded, explicitly opt-in, at-least-once relay. No JDBC transaction is held
 * during network calls. A crash after ACK before marking delivered may repeat
 * the SAME eventId, which Audit inserts idempotently.
 */
@Component
@ConditionalOnProperty(prefix = "aetheris.outbox", name = "enabled", havingValue = "true")
public class UserOutboxRelay {
    private static final Logger LOG = LoggerFactory.getLogger(UserOutboxRelay.class);
    private final UserOutboxStore store;
    private final RabbitTemplate rabbit;
    private final int batch;

    public UserOutboxRelay(UserOutboxStore store, RabbitTemplate rabbit,
                           @Value("${aetheris.outbox.batch:8}") int batch) {
        this.store = store;
        this.rabbit = rabbit;
        if (batch < 1 || batch > 32) {
            throw new IllegalArgumentException("Outbox relay batch must be between 1 and 32");
        }
        this.batch = batch;
    }

    @PostConstruct
    public void preflight() {
        store.validateSchema();
        if (!(rabbit.getConnectionFactory() instanceof CachingConnectionFactory cf) ||
                cf.getPublisherConfirmType() != CachingConnectionFactory.ConfirmType.CORRELATED) {
            throw new IllegalStateException("Outbox requires correlated RabbitMQ publisher confirms");
        }
        rabbit.setMandatory(true);
    }

    @Scheduled(fixedDelayString = "${aetheris.outbox.poll-ms:350}")
    public void pump() {
        for (int i = 0; i < batch; i++) {
            var claimed = store.leaseNext();
            if (claimed.isEmpty()) {
                return;
            }
            sendOne(claimed.get());
        }
    }

    public void sendOne(UserOutboxStore.LeasedEvent event) {
        try {
            // Correlation future is completed after broker ACK; returned messages
            // are populated before future completion when returns are enabled.
            CorrelationData correlation = new CorrelationData(event.eventId().toString());
            rabbit.convertAndSend(UserEventPublisher.EXCHANGE, event.eventType(),
                    event.message(), message -> {
                        message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                        return message;
                    }, correlation);
            CorrelationData.Confirm confirm =
                    correlation.getFuture().get(8, TimeUnit.SECONDS);
            if (!confirm.isAck() || correlation.getReturned() != null) {
                throw new IllegalStateException("Broker did not confirm routed outbox event");
            }
            if (!store.markDelivered(event)) {
                LOG.warn("Outbox claim expired after ACK; stable eventId may be replayed");
            }
        } catch (Exception failure) {
            // Never log event payload, PII, tokens or raw broker exception text.
            store.markFailure(event);
            LOG.warn("Outbox publication failed; event retained for bounded retry (failure class {})",
                    failure.getClass().getSimpleName());
            if (failure instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
