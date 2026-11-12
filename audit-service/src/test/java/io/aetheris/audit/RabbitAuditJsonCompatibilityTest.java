package io.aetheris.audit;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RabbitAuditJsonCompatibilityTest {

    @Test
    void legacyUserEventEnvelopeRoundTripsThroughJackson3Consumer() {
        JacksonJsonMessageConverter writer = new JacksonJsonMessageConverter();
        JacksonJsonMessageConverter reader = new RabbitAuditConfig().rabbitMessageConverter();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventId", "event-123");
        payload.put("eventType", "user.created");
        payload.put("occurredAt", "2026-10-23T00:00:00Z");
        payload.put("userId", 42L);
        payload.put("name", "Test User");
        payload.put("email", "test@example.invalid");

        Message wire = writer.toMessage(payload, new MessageProperties());
        assertEquals(MessageProperties.CONTENT_TYPE_JSON, wire.getMessageProperties().getContentType());
        assertEquals("java.util.LinkedHashMap", wire.getMessageProperties().getHeaders().get("__TypeId__"));
        Object decoded = reader.fromMessage(wire);
        assertInstanceOf(Map.class, decoded);
        Map<?, ?> result = (Map<?, ?>) decoded;
        for (String key : new String[]{"eventId", "eventType", "occurredAt", "name", "email"}) {
            assertEquals(payload.get(key), result.get(key), key);
        }
        assertEquals(42L, ((Number) result.get("userId")).longValue());
    }

    @Test
    void untrustedSerializedTypeCannotBeDeserialized() {
        MessageProperties props = new MessageProperties();
        props.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        props.setHeader("__TypeId__", "malicious.example.UntrustedType");
        Message wire = new Message("{}".getBytes(StandardCharsets.UTF_8), props);
        assertThrows(RuntimeException.class,
                () -> new RabbitAuditConfig().rabbitMessageConverter().fromMessage(wire));
    }
}
