package io.aetheris.users.events;

import io.jsonwebtoken.Jwts;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Explicit opt-in User -> Audit origin authentication. Signed AFTER an outbox
 * lease is claimed so backlogged rows obtain a fresh short-lived proof.
 * Private key stays with User; Audit receives only a pinned RSA public key.
 * This is NOT a recovery epoch, tenant membership or replay-time revocation.
 */
@Component
public final class UserEventOriginSigner {
    static final String ISSUER = "aetheris-user-event-producer";
    static final String AUDIENCE = "aetheris-audit-domain-event";
    static final String SUBJECT = "aetheris-user-service";
    private static final int TTL_SECONDS = 300;
    private final PrivateKey privateKey;
    private final String keyId;

    @Autowired
    public UserEventOriginSigner(
            @Value("${aetheris.audit-origin.enabled:false}") boolean enabled,
            @Value("${aetheris.audit-origin.private-key-pem:}") String pem,
            @Value("${aetheris.audit-origin.key-id:}") String keyId) {
        if (!enabled) {
            if ((pem != null && !pem.isBlank()) || (keyId != null && !keyId.isBlank())) {
                throw new IllegalArgumentException("Audit origin key configured while signing disabled");
            }
            this.privateKey = null;
            this.keyId = null;
            return;
        }
        if (keyId == null || !keyId.matches("[A-Za-z0-9._-]{1,64}")) {
            throw new IllegalArgumentException("Audit origin requires a pinned key ID");
        }
        try {
            if (pem == null || !pem.trim().startsWith("-----BEGIN PRIVATE KEY-----")) {
                throw new IllegalArgumentException("Expected PKCS8 RSA private key PEM");
            }
            String body = pem.replace("\\n", "\n")
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replaceAll("\\s", "");
            byte[] bytes = Base64.getDecoder().decode(body);
            PrivateKey parsed = KeyFactory.getInstance("RSA")
                    .generatePrivate(new PKCS8EncodedKeySpec(bytes));
            if (!(parsed instanceof RSAPrivateKey rsa) || rsa.getModulus().bitLength() < 3072) {
                throw new IllegalArgumentException("RSA origin key must be at least 3072 bits");
            }
            this.privateKey = parsed;
            this.keyId = keyId;
        } catch (Exception ignored) {
            // Do not disclose supplied key material, parsing exceptions or raw input.
            throw new IllegalArgumentException("Invalid Audit origin RSA private key");
        }
    }

    static UserEventOriginSigner disabled() {
        return new UserEventOriginSigner(false, "", "");
    }

    public Map<String, Object> sign(Map<String, Object> original) {
        if (privateKey == null) return original;
        if (original == null || (original.size() != 6 && original.size() != 7)
                || !original.keySet().stream().allMatch(k ->
                        java.util.Set.of("eventId", "eventType", "occurredAt",
                                "userId", "name", "email", "tenantId").contains(k))) {
            throw new IllegalArgumentException("Unexpected User domain event fields");
        }
        String id = (String) original.get("eventId");
        if (id == null || !UUID.fromString(id).toString().equals(id)
                || !(original.get("userId") instanceof Number userId)
                || userId.longValue() <= 0 || userId.doubleValue() != userId.longValue()) {
            throw new IllegalArgumentException("Invalid signed User event identifiers");
        }
        Instant occurred = Instant.parse((String) original.get("occurredAt"));
        if (occurred == null) throw new IllegalArgumentException("Missing event time");
        String type = (String) original.get("eventType");
        String name = (String) original.get("name");
        String email = (String) original.get("email");
        if (!("user.created".equals(type) || "user.deleted".equals(type))
                || name == null || email == null) {
            throw new IllegalArgumentException("Unrecognized User event");
        }

        Instant now = Instant.now();
        var builder = Jwts.builder()
                .issuer(ISSUER).audience().add(AUDIENCE).and()
                .subject(SUBJECT).id(id)
                .claim("eventType", type)
                .claim("occurredAt", original.get("occurredAt"))
                .claim("userId", userId.longValue())
                .claim("name", name)
                .claim("email", email)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(TTL_SECONDS)));
        Object tenant = original.get("tenantId");
        if (tenant != null) {
            if (!(tenant instanceof String value) ||
                    !UUID.fromString(value).toString().equals(value)) {
                throw new IllegalArgumentException("Invalid signed tenant identifier");
            }
            builder.claim("tenantId", tenant);
        }
        String proof = builder.header().keyId(keyId).and()
                .signWith(privateKey, Jwts.SIG.RS256).compact();
        Map<String, Object> event = new LinkedHashMap<>(original);
        event.put("originProof", proof);
        return event;
    }
}
