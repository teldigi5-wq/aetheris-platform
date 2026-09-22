package io.aetheris.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FallbackControllerTest {

    @Test
    void everyFallbackReturnsRetryableServiceUnavailablePayload() {
        FallbackController controller = new FallbackController();

        assertFallback(controller.usersFallback(), "user-service");
        assertFallback(controller.eventsFallback(), "audit-service");
        assertFallback(controller.identityFallback(), "identity-service");
        assertFallback(controller.orchestratorFallback(), "orchestrator-service");
    }

    private static void assertFallback(ResponseEntity<Map<String, Object>> response, String service) {
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(503, response.getBody().get("status"));
        assertEquals("Service Unavailable", response.getBody().get("error"));
        assertEquals(service, response.getBody().get("service"));
        assertEquals(Boolean.TRUE, response.getBody().get("retryable"));
        assertNotNull(response.getBody().get("timestamp"));
    }
}
