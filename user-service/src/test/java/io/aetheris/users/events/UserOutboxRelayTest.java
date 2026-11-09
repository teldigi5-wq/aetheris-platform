package io.aetheris.users.events;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.core.MessagePostProcessor;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class UserOutboxRelayTest {
    private UserOutboxStore.LeasedEvent event() {
        return new UserOutboxStore.LeasedEvent(UUID.randomUUID(), "user.created",
                Instant.parse("2026-11-06T06:00:00Z"), 13L,
                "Synthetic", "synthetic@example.test", null, 1, UUID.randomUUID());
    }

    @Test
    void onlyPositiveConfirmedBrokerDeliveryMarksRowDelivered() {
        UserOutboxStore store = mock(UserOutboxStore.class);
        RabbitTemplate rabbit = mock(RabbitTemplate.class);
        var e = event();
        doAnswer(call -> {
            CorrelationData cd = call.getArgument(4);
            cd.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbit).convertAndSend(anyString(), anyString(), any(),
                any(MessagePostProcessor.class), any(CorrelationData.class));
        new UserOutboxRelay(store,rabbit,8).sendOne(e);
        verify(store).markDelivered(e);
        verify(store, never()).markFailure(any());
        var captor = org.mockito.ArgumentCaptor.forClass(CorrelationData.class);
        verify(rabbit).convertAndSend(eq(UserEventPublisher.EXCHANGE),
                eq("user.created"), any(), any(MessagePostProcessor.class), captor.capture());
        assertEquals(e.eventId().toString(),captor.getValue().getId());
    }

    @Test
    void brokerNackNeverMarksDeliveredAndRetainsRetry() {
        UserOutboxStore store = mock(UserOutboxStore.class);
        RabbitTemplate rabbit = mock(RabbitTemplate.class);
        var e = event();
        doAnswer(call -> {
            CorrelationData cd = call.getArgument(4);
            cd.getFuture().complete(new CorrelationData.Confirm(false, "simulated NACK"));
            return null;
        }).when(rabbit).convertAndSend(anyString(), anyString(), any(),
                any(MessagePostProcessor.class), any(CorrelationData.class));
        new UserOutboxRelay(store,rabbit,8).sendOne(e);
        verify(store).markFailure(e);
        verify(store, never()).markDelivered(any());
    }

    @Test
    void thrownBrokerFailureLeavesOutboxAvailableForRetry() {
        UserOutboxStore store=mock(UserOutboxStore.class);
        RabbitTemplate rabbit=mock(RabbitTemplate.class);
        var e=event();
        doThrow(new IllegalStateException("synthetic broker down")).when(rabbit)
                .convertAndSend(anyString(),anyString(),any(),
                        any(MessagePostProcessor.class),any(CorrelationData.class));
        new UserOutboxRelay(store,rabbit,8).sendOne(e);
        verify(store).markFailure(e);
        verify(store, never()).markDelivered(any());
    }

    @Test
    void leasedEventAlwaysReusesOriginalEventIdForAuditDeduplication() {
        var e=event();
        assertEquals(e.eventId().toString(), e.message().get("eventId"));
        assertEquals(e.eventId().toString(), e.message().get("eventId"));
        assertFalse(e.message().containsKey("tenantId"));
    }

    @Test
    void invalidBatchRejectedAtConstruction() {
        assertThrows(IllegalArgumentException.class,
                () -> new UserOutboxRelay(mock(UserOutboxStore.class),
                        mock(RabbitTemplate.class),0));
        assertThrows(IllegalArgumentException.class,
                () -> new UserOutboxRelay(mock(UserOutboxStore.class),
                        mock(RabbitTemplate.class),33));
    }
}
