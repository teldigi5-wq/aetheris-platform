package io.aetheris.gateway;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/fallback")
public class FallbackController {

    @RequestMapping("/users")
    public ResponseEntity<Map<String, Object>> usersFallback() {
        return unavailable("user-service", "User service is temporarily unavailable");
    }

    @RequestMapping("/events")
    public ResponseEntity<Map<String, Object>> eventsFallback() {
        return unavailable("audit-service", "Audit service is temporarily unavailable");
    }

    @RequestMapping("/identity")
    public ResponseEntity<Map<String, Object>> identityFallback() {
        return unavailable("identity-service", "Identity service is temporarily unavailable");
    }

    private ResponseEntity<Map<String, Object>> unavailable(String service, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", HttpStatus.SERVICE_UNAVAILABLE.value());
        body.put("error", "Service Unavailable");
        body.put("service", service);
        body.put("message", message);
        body.put("retryable", true);
        body.put("timestamp", Instant.now().toString());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body);
    }
}
