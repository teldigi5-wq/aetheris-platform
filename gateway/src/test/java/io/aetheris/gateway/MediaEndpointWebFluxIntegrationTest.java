package io.aetheris.gateway;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

class MediaEndpointWebFluxIntegrationTest {
    private static final String SECRET = "0123456789abcdef0123456789abcdef";

    private static String token(long uid, String... scopes) {
        return Jwts.builder().subject("verified@example.com")
                .claim("uid", uid).claim("role", "DEVELOPER")
                .claim("scopes", List.of(scopes))
                .expiration(Date.from(Instant.now().plusSeconds(300)))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }

    private static WebTestClient client(boolean stagingEnabled) {
        MediaStagingController controller = new MediaStagingController(new StagedMediaJobService(stagingEnabled));
        return WebTestClient.bindToController(controller)
                .webFilter(new MediaJobAuthWebFilter(SECRET))
                .configureClient().build();
    }

    @Test
    void unsignedMediaCapabilityCallDoesNotReachController() {
        client(true).get().uri("/api/media/capabilities")
                .header("X-Aetheris-Client", "syntra-native")
                .exchange().expectStatus().isUnauthorized();
    }

    @Test
    void verifiedOwnerCanReadCapabilitiesButCannotEnableGpuExecution() {
        client(false).get().uri("/api/media/capabilities")
                .header("X-Aetheris-Client", "syntra-native")
                .header("Authorization", "Bearer " + token(42, "media:read"))
                .exchange().expectStatus().isOk()
                .expectBody()
                .jsonPath("$.stagingEnabled").isEqualTo(false)
                .jsonPath("$.physicalPcStatus").isEqualTo("BLOCKED_PENDING_HARDWARE")
                .jsonPath("$.gpuJobStarted").isEqualTo(false);
    }

    @Test
    void defaultDisabledPostIs503EvenWithValidJwt() {
        client(false).post().uri("/api/media/jobs")
                .header("X-Aetheris-Client", "syntra-native")
                .header("Authorization", "Bearer " + token(42, "media:write"))
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"kind\":\"IMAGE\",\"prompt\":\"sky\",\"width\":512,\"height\":512}")
                .exchange().expectStatus().isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void stagedJobsAreNeverExposedAcrossAccounts() {
        WebTestClient web = client(true);
        String key = UUID.randomUUID().toString();
        byte[] reply = web.post().uri("/api/media/jobs")
                .header("X-Aetheris-Client", "syntra-native")
                .header("Authorization", "Bearer " + token(42, "media:write"))
                .header("X-Aetheris-Owner-Id", "999")
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"kind\":\"IMAGE\",\"prompt\":\"sensitive prompt\",\"width\":512,\"height\":512}")
                .exchange().expectStatus().isAccepted()
                .expectBody().jsonPath("$.state").isEqualTo("STAGED_NOT_EXECUTED")
                .jsonPath("$.gpuJobStarted").isEqualTo(false)
                .returnResult().getResponseBody();

        org.junit.jupiter.api.Assertions.assertNotNull(reply);
        String output = new String(reply, StandardCharsets.UTF_8);
        org.junit.jupiter.api.Assertions.assertFalse(output.contains("sensitive prompt"));
        String id = com.fasterxml.jackson.databind.json.JsonMapper.builder().build()
                .readTree(output).get("id").asText();
        web.get().uri("/api/media/jobs/{id}", id)
                .header("X-Aetheris-Client", "syntra-native")
                .header("Authorization", "Bearer " + token(999, "media:read"))
                .exchange().expectStatus().isNotFound();
        web.delete().uri("/api/media/jobs/{id}", id)
                .header("X-Aetheris-Client", "syntra-native")
                .header("Authorization", "Bearer " + token(999, "media:write"))
                .exchange().expectStatus().isNotFound();
        web.get().uri("/api/media/jobs/{id}", id)
                .header("X-Aetheris-Client", "syntra-native")
                .header("Authorization", "Bearer " + token(42, "media:read"))
                .exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.id").isEqualTo(id);
    }
}
