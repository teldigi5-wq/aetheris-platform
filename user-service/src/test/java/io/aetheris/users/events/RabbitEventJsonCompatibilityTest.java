package io.aetheris.users.events;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RabbitEventJsonCompatibilityTest {

    @Test
    void jackson3PublisherPreservesUserEventJsonAndHeaders() {
        JacksonJsonMessageConverter converter = new RabbitEventConfig().rabbitMessageConverter();
        Map<String, Object> legacyEvent = new LinkedHashMap<>();
        legacyEvent.put("eventId", "event-123");
        legacyEvent.put("eventType", "user.deleted");
        legacyEvent.put("occurredAt", "2026-10-23T00:00:00Z");
        legacyEvent.put("userId", 42L);
        legacyEvent.put("name", "Test User");
        legacyEvent.put("email", "test@example.invalid");

        Message wire = converter.toMessage(legacyEvent, new MessageProperties());
        assertEquals(MessageProperties.CONTENT_TYPE_JSON, wire.getMessageProperties().getContentType());
        assertEquals("java.util.LinkedHashMap", wire.getMessageProperties().getHeaders().get("__TypeId__"));
        String json = new String(wire.getBody(), StandardCharsets.UTF_8);
        for (String name : new String[]{"eventId", "eventType", "occurredAt", "userId", "name", "email"}) {
            assertTrue(json.contains("\"" + name + "\""), name);
        }
        Object decoded = new JacksonJsonMessageConverter().fromMessage(wire);
        assertInstanceOf(Map.class, decoded);
        Map<?, ?> result = (Map<?, ?>) decoded;
        assertEquals("user.deleted", result.get("eventType"));
        assertEquals(42L, ((Number) result.get("userId")).longValue());
    }
}
