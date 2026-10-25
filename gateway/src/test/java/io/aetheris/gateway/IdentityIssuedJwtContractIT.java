package io.aetheris.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Cross-module compatibility: read actual Identity JwtService.issue() output
 * from the test-only target directory. Explicit -Dtest=IdentityIssuedJwtContractIT.
 * This proves Gateway filter consumption, NOT a live Gateway HTTP proxy route.
 */
class IdentityIssuedJwtContractIT {
    private static final String SECRET =
        "test-only-direct-backend-auth-hmac-secret-at-least-32-bytes";
    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(SECRET);

    private String issued(String role) throws Exception {
        Path root = Path.of("..", "identity-service", "target", "identity-jwt-contract")
            .toAbsolutePath().normalize();
        assertEquals("IDENTITY_ISSUER_DIRECT_JWT_CONTRACT_V1",
            Files.readString(root.resolve("test-only.marker"), StandardCharsets.US_ASCII).trim());
        return Files.readString(root.resolve(role + ".jwt"), StandardCharsets.US_ASCII).trim();
    }

    private ServerWebExchange forwarded(String path, String jwt, HttpStatus denial) {
        MockServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get(path)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt)
                .header("X-Aetheris-User-Id", "999")
                .header("X-Aetheris-User-Role", "ADMIN")
                .header("X-Aetheris-Scopes", "users:write")
                .build());
        AtomicReference<ServerWebExchange> next = new AtomicReference<>();
        filter.filter(exchange, auth -> { next.set(auth); return Mono.empty(); }).block();
        if (denial != null) {
            assertNull(next.get());
            assertEquals(denial, exchange.getResponse().getStatusCode());
            assertNull(exchange.getAttribute(JwtAuthenticationFilter.VERIFIED_USER_ID_ATTRIBUTE));
            return null;
        }
        assertNotNull(next.get());
        assertEquals("Bearer " + jwt,
            next.get().getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION));
        assertNotEquals("999", next.get().getRequest().getHeaders().getFirst("X-Aetheris-User-Id"));
        return next.get();
    }

    @Test
    void actualIdentityAdminTokenPassesBothAdministrativeRoutes() throws Exception {
        var users = forwarded("/api/users", issued("admin"), null);
        assertEquals("42", users.getAttribute(JwtAuthenticationFilter.VERIFIED_USER_ID_ATTRIBUTE));
        assertEquals("ADMIN", users.getRequest().getHeaders().getFirst("X-Aetheris-User-Role"));
        assertTrue(users.getRequest().getHeaders().getFirst("X-Aetheris-Scopes")
            .contains("users:read"));
        var audit = forwarded("/api/events", issued("admin"), null);
        assertEquals("42", audit.getRequest().getHeaders().getFirst("X-Aetheris-User-Id"));
    }

    @Test
    void actualIdentityNonadminTokenIsDeniedAtUserAndAuditBeforeBackend() throws Exception {
        for (String role : new String[]{"developer", "consumer"}) {
            forwarded("/api/users", issued(role), HttpStatus.FORBIDDEN);
            forwarded("/api/events", issued(role), HttpStatus.FORBIDDEN);
        }
    }

    @Test
    void actualIdentityConsumerStillPassesOrchestratorRead() throws Exception {
        ServerWebExchange next = forwarded("/api/orchestrator", issued("consumer"), null);
        assertEquals("API_CONSUMER", next.getRequest().getHeaders().getFirst("X-Aetheris-User-Role"));
        assertEquals("44", next.getRequest().getHeaders().getFirst("X-Aetheris-User-Id"));
    }

    @Test
    void expiredOrAlteredActualIssuerTokensNeverRoute() throws Exception {
        forwarded("/api/users", issued("expired"), HttpStatus.UNAUTHORIZED);
        forwarded("/api/users", issued("tampered"), HttpStatus.UNAUTHORIZED);
        forwarded("/api/events", issued("tampered"), HttpStatus.UNAUTHORIZED);
    }
}
