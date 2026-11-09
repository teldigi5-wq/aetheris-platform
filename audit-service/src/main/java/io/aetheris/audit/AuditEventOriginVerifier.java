package io.aetheris.audit;

import io.jsonwebtoken.Jwts;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Real listener opt-in origin verification, BEFORE Audit persistence.
 * RSA public key only; cannot mint an event. This authenticates a publisher
 * holding the private key but does NOT prove live tenant membership or epoch.
 */
@Component
public final class AuditEventOriginVerifier {
    private static final String ISSUER = "aetheris-user-event-producer";
    private static final String AUDIENCE = "aetheris-audit-domain-event";
    private static final String SUBJECT = "aetheris-user-service";
    private final PublicKey publicKey;
    private final String keyId;

    @Autowired
    public AuditEventOriginVerifier(
            @Value("${aetheris.audit-origin.required:false}") boolean required,
            @Value("${aetheris.audit-origin.public-key-pem:}") String pem,
            @Value("${aetheris.audit-origin.key-id:}") String keyId) {
        if (!required) {
            if ((pem != null && !pem.isBlank()) || (keyId != null && !keyId.isBlank())) {
                throw new IllegalArgumentException("Audit origin verifier key configured while disabled");
            }
            this.publicKey = null;
            this.keyId = null;
            return;
        }
        if (keyId == null || !keyId.matches("[A-Za-z0-9._-]{1,64}")) {
            throw new IllegalArgumentException("Audit origin verifier requires pinned key ID");
        }
        try {
            if (pem == null || !pem.trim().startsWith("-----BEGIN PUBLIC KEY-----")) {
                throw new IllegalArgumentException("Expected X509 public key PEM");
            }
            String body = pem.replace("\\n", "\n")
                    .replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .replaceAll("\\s", "");
            PublicKey parsed = KeyFactory.getInstance("RSA").generatePublic(
                    new X509EncodedKeySpec(Base64.getDecoder().decode(body)));
            if (!(parsed instanceof RSAPublicKey rsa) || rsa.getModulus().bitLength() < 3072) {
                throw new IllegalArgumentException("RSA origin verifier must use >=3072 bits");
            }
            this.publicKey = parsed;
            this.keyId = keyId;
        } catch (Exception ignored) {
            throw new IllegalArgumentException("Invalid Audit origin RSA public key");
        }
    }

    public static AuditEventOriginVerifier disabled() {
        return new AuditEventOriginVerifier(false, "", "");
    }

    public void verify(Map<String, Object> envelope, UserDomainEvent event) {
        Object raw = envelope.get("originProof");
        if (publicKey == null) {
            if (envelope.containsKey("originProof")) {
                throw new IllegalArgumentException("Unexpected event proof in disabled mode");
            }
            return;
        }
        if (!(raw instanceof String proof) || proof.isBlank() || proof.length() > 4096) {
            throw new IllegalArgumentException("Required event proof missing or oversized");
        }
        try {
            var signed = Jwts.parser().verifyWith(publicKey)
                    .requireIssuer(ISSUER).requireAudience(AUDIENCE)
                    .build().parseSignedClaims(proof);
            if (!"RS256".equals(signed.getHeader().getAlgorithm())
                    || !keyId.equals(signed.getHeader().getKeyId())) {
                throw new IllegalArgumentException("Unexpected origin verification key");
            }
            var c = signed.getPayload();
            Date issued = c.getIssuedAt(), expires = c.getExpiration();
            Instant now = Instant.now();
            if (issued == null || expires == null
                    || issued.toInstant().isAfter(now.plusSeconds(30))
                    || !expires.after(issued)
                    || expires.toInstant().isAfter(issued.toInstant().plusSeconds(300))
                    || !SUBJECT.equals(c.getSubject())
                    || !(c.get("userId") instanceof Number userId)
                    || userId.longValue() <= 0
                    || userId.doubleValue() != userId.longValue()
                    || !Objects.equals(c.getId(), event.eventId())
                    || !Objects.equals(c.get("eventType"), event.eventType())
                    || !Objects.equals(c.get("occurredAt"), event.occurredAt().toString())
                    || userId.longValue() != event.userId()
                    || !Objects.equals(c.get("name"), event.name())
                    || !Objects.equals(c.get("email"), event.email())) {
                throw new IllegalArgumentException("Signed event claims do not match envelope");
            }
            String tid = c.get("tenantId", String.class);
            if (event.tenantId() == null) {
                if (tid != null || c.containsKey("tenantId")) {
                    throw new IllegalArgumentException("Signed event tenant mismatch");
                }
            } else if (!event.tenantId().toString().equals(tid)
                    || !UUID.fromString(tid).toString().equals(tid)) {
                throw new IllegalArgumentException("Signed event tenant mismatch");
            }
        } catch (Exception badProof) {
            // The listener substitutes one sanitized poison-message reason.
            // Never log a token, raw source event, tenant or key.
            throw new IllegalArgumentException("Invalid signed event origin");
        }
    }
}
