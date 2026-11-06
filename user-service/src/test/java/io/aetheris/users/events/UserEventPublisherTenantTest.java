package io.aetheris.users.events;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class UserEventPublisherTenantTest {
    private static final UUID OWNED =
            UUID.fromString("e398c9b9-23ea-473a-8240-8c42a92f7a34");

    @Test
    @SuppressWarnings("unchecked")
    void tenantCreateAndDeletePublishCanonicalOwnerFromTypedServerUuid() {
        RabbitTemplate rabbit = mock(RabbitTemplate.class);
        UserEventPublisher publisher = new UserEventPublisher(rabbit);
        publisher.userCreated(10L, "Scoped", "owned@example.test", OWNED);
        publisher.userDeleted(10L, "Scoped", "owned@example.test", OWNED);
        var captor = org.mockito.ArgumentCaptor.forClass(Object.class);
        verify(rabbit).convertAndSend(eq(UserEventPublisher.EXCHANGE),
                eq("user.created"), captor.capture());
        Object record = captor.getValue();
        assertTrue(record instanceof Map<?, ?>);
        assertEquals(OWNED.toString(), ((Map<String, Object>) record).get("tenantId"));
        assertEquals(10L, ((Map<String, Object>) record).get("userId"));
        verify(rabbit).convertAndSend(eq(UserEventPublisher.EXCHANGE),
                eq("user.deleted"), captor.capture());
        assertEquals(OWNED.toString(),
                ((Map<String, Object>) captor.getValue()).get("tenantId"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void legacyGlobalAdminEventExplicitlyHasNoInventedTenant() {
        RabbitTemplate rabbit = mock(RabbitTemplate.class);
        new UserEventPublisher(rabbit).userCreated(11L, "Legacy", "legacy@example.test");
        var event = org.mockito.ArgumentCaptor.forClass(Object.class);
        verify(rabbit).convertAndSend(eq(UserEventPublisher.EXCHANGE),
                eq("user.created"), event.capture());
        assertFalse(((Map<String, Object>) event.getValue()).containsKey("tenantId"));
    }

    @Test
    void tenantPublisherCannotBeCalledWithoutVerifiedOwner() {
        RabbitTemplate rabbit = mock(RabbitTemplate.class);
        UserEventPublisher publisher = new UserEventPublisher(rabbit);
        assertThrows(NullPointerException.class,
                () -> publisher.userCreated(17L,"NoOwner","unowned@example.test",null));
        assertThrows(NullPointerException.class,
                () -> publisher.userDeleted(17L,"NoOwner","unowned@example.test",null));
        verifyNoInteractions(rabbit);
    }
}
