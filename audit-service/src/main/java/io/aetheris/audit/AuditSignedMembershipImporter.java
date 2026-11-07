package io.aetheris.audit;

import io.jsonwebtoken.Jwts;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Disabled-by-default, authority-signed membership delta importer.
 *
 * This is a verification and write boundary, NOT an authenticated transport,
 * authoritative source, identity service or rollback-proof external witness.
 * There is deliberately no HTTP, RabbitMQ or auto-provisioning endpoint.
 * The caller must be a separately authorized and audited reconciler.
 */
@Component
public class AuditSignedMembershipImporter {
    static final String ISSUER = "aetheris-membership-authority";
    static final String AUDIENCE = "aetheris-audit-membership-reconciliation";
    static final String SUBJECT = "tenant-user-membership";
    private static final Set<String> ALLOWED_CLAIMS = Set.of(
            "iss", "aud", "sub", "jti", "iat", "exp",
            "tenantId", "userId", "status", "validSince", "revision");
    private static final Duration MAX_PROOF_AGE = Duration.ofMinutes(5);
    private final PublicKey authorityKey;
    private final String authorityKeyId;
    private final JdbcTemplate jdbc;

    /**
     * Audit Spring Boot process is a network-facing RabbitMQ consumer and
     * HTTP API. It MUST NEVER hold the membership writer credential, even if
     * that credential belongs to a distinct PostgreSQL role.
     *
     * A future reconciler must be a separately deployed process, with its
     * own workload identity, trusted source, and owner-approved key custody.
     * Explicit test constructor below remains available for isolated CI.
     */
    @Autowired
    public AuditSignedMembershipImporter(
            @Value("${aetheris.audit-membership.import-enabled:false}") boolean enabled,
            @Value("${aetheris.audit-membership.authority-public-key-pem:}") String publicPem,
            @Value("${aetheris.audit-membership.authority-key-id:}") String keyId,
            @Value("${aetheris.audit-origin.key-id:}") String eventSigningKeyId,
            JdbcTemplate auditJdbc,
            @Value("${aetheris.audit-membership.writer-db-url:}") String writerUrl,
            @Value("${aetheris.audit-membership.writer-db-username:}") String writerUsername,
            @Value("${aetheris.audit-membership.writer-db-password:}") String writerPassword,
            @Value("${spring.datasource.url:}") String auditDbUrl,
            @Value("${spring.datasource.username:}") String auditDbUsername) {
        this(false, publicPem, keyId, eventSigningKeyId,
                applicationOnlyJdbc(enabled, auditJdbc, writerUrl,
                        writerUsername, writerPassword));
    }

    private static JdbcTemplate applicationOnlyJdbc(
            boolean importEnabled, JdbcTemplate auditJdbc,
            String writerUrl, String writerUsername, String writerPassword) {
        if (importEnabled || hasValue(writerUrl) || hasValue(writerUsername)
                || hasValue(writerPassword)) {
            // No raw credentials, URLs or claims in errors.
            throw new IllegalStateException(
                    "Audit process cannot host membership reconciliation");
        }
        if (auditJdbc == null) {
            throw new IllegalStateException("Audit database is required");
        }
        return auditJdbc;
    }

    /** Narrow explicit test-only constructor; never used for Spring wiring. */
    public AuditSignedMembershipImporter(boolean enabled, String publicPem,
                                         String keyId, String eventSigningKeyId,
                                         JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        if (!enabled) {
            if ((publicPem != null && !publicPem.isBlank())
                    || (keyId != null && !keyId.isBlank())) {
                throw new IllegalArgumentException(
                        "Membership authority key supplied while importer disabled");
            }
            this.authorityKey = null;
            this.authorityKeyId = null;
            return;
        }
        if (jdbc == null || keyId == null || !keyId.matches("[A-Za-z0-9._-]{1,64}")
                || keyId.equals(eventSigningKeyId)) {
            throw new IllegalArgumentException(
                    "Membership importer requires separate pinned authority identity");
        }
        try {
            if (publicPem == null
                    || !publicPem.trim().startsWith("-----BEGIN PUBLIC KEY-----")) {
                throw new IllegalArgumentException("Invalid authority public key");
            }
            String der = publicPem.replace("\\n", "\n")
                    .replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .replaceAll("\\s", "");
            PublicKey key = KeyFactory.getInstance("RSA").generatePublic(
                    new X509EncodedKeySpec(Base64.getDecoder().decode(der)));
            if (!(key instanceof RSAPublicKey rsa)
                    || rsa.getModulus().bitLength() < 3072) {
                throw new IllegalArgumentException("Authority RSA key too weak");
            }
            this.authorityKey = key;
            this.authorityKeyId = keyId;
        } catch (Exception ignored) {
            // Neither key material nor parsing exceptions appear in logs.
            throw new IllegalArgumentException("Invalid membership authority public key");
        }
    }

    private static boolean hasValue(String text) {
        return text != null && !text.isBlank();
    }

    /**
     * Apply one exact signed delta. SQL enforces single-database monotonicity
     * under concurrency. No update is derived from a User event or JWT issued
     * by the separate User event-origin private key.
     *
     * Duplicate, stale, forged and backdated reactivation are all denied.
     * JDBC outages propagate for retry, never disguised as bad signatures.
     */
    @Transactional
    public void importSigned(String proof) {
        if (authorityKey == null) {
            throw new IllegalStateException("Membership authority importer is disabled");
        }
        final Delta d = verify(proof);
        List<Long> inserted = jdbc.query(
                """
                INSERT INTO audit_user_tenant_membership_fence
                    (tenant_id, user_id, status, valid_since, revision)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (tenant_id, user_id) DO UPDATE SET
                    status = EXCLUDED.status,
                    valid_since = EXCLUDED.valid_since,
                    revision = EXCLUDED.revision
                WHERE EXCLUDED.revision > audit_user_tenant_membership_fence.revision
                  AND EXCLUDED.valid_since >= audit_user_tenant_membership_fence.valid_since
                  AND NOT (
                      audit_user_tenant_membership_fence.status = 'REVOKED'
                      AND EXCLUDED.status = 'ACTIVE'
                      AND EXCLUDED.valid_since <= audit_user_tenant_membership_fence.valid_since
                  )
                RETURNING revision
                """,
                (rs, row) -> rs.getLong("revision"),
                d.tenantId(), d.userId(), d.status(),
                Timestamp.from(d.validSince()), d.revision());
        if (inserted.size() != 1 || inserted.get(0) != d.revision()) {
            // Do not echo tenant ID, user ID, JWT, claims or public key.
            throw new IllegalArgumentException(
                    "Stale or conflicting membership authority revision");
        }
    }

    private Delta verify(String proof) {
        if (proof == null || proof.isBlank() || proof.length() > 4096) {
            throw denied();
        }
        try {
            var signed = Jwts.parser().verifyWith(authorityKey)
                    .requireIssuer(ISSUER).requireAudience(AUDIENCE)
                    .build().parseSignedClaims(proof);
            var h = signed.getHeader();
            var c = signed.getPayload();
            if (!"RS256".equals(h.getAlgorithm())
                    || !authorityKeyId.equals(h.getKeyId())
                    || !SUBJECT.equals(c.getSubject())
                    || !ALLOWED_CLAIMS.containsAll(c.keySet())
                    || c.size() != ALLOWED_CLAIMS.size()) {
                throw denied();
            }
            Instant issued = requireDate(c.getIssuedAt());
            Instant expiry = requireDate(c.getExpiration());
            Instant now = Instant.now();
            if (issued.isAfter(now.plusSeconds(30))
                    || expiry.isAfter(issued.plus(MAX_PROOF_AGE))
                    || !expiry.isAfter(issued)
                    || expiry.isBefore(now)
                    || issued.isBefore(now.minus(MAX_PROOF_AGE))) {
                throw denied();
            }
            canonicalUuid(c.getId());
            UUID tenant = canonicalUuid(c.get("tenantId", String.class));
            long user = exactPositiveLong(c.get("userId"));
            long revision = exactPositiveLong(c.get("revision"));
            String status = c.get("status", String.class);
            if (!"ACTIVE".equals(status) && !"REVOKED".equals(status)) {
                throw denied();
            }
            String text = c.get("validSince", String.class);
            Instant since = Instant.parse(text);
            if (!since.toString().equals(text)
                    || since.isAfter(now.plusSeconds(30))) {
                throw denied();
            }
            return new Delta(tenant,user,status,since,revision);
        } catch (Exception e) {
            throw denied();
        }
    }

    private static Instant requireDate(Date date) {
        if (date == null) throw denied();
        return date.toInstant();
    }

    private static UUID canonicalUuid(String value) {
        UUID parsed = UUID.fromString(value);
        if (!parsed.toString().equals(value)) throw denied();
        return parsed;
    }

    private static long exactPositiveLong(Object value) {
        if (!(value instanceof Long || value instanceof Integer || value instanceof Short)
                || ((Number) value).longValue() < 1) throw denied();
        return ((Number) value).longValue();
    }

    private static IllegalArgumentException denied() {
        return new IllegalArgumentException("Invalid membership authority proof");
    }

    private record Delta(UUID tenantId, long userId, String status,
                         Instant validSince, long revision) {}
}
