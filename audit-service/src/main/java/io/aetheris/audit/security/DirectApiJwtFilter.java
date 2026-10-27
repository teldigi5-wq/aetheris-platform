package io.aetheris.audit.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.SecretKey;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.Clock;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Objects;

/**
 * Defense in depth for routes reachable without the API Gateway.
 * Verifies the ORIGINAL signed client bearer JWT independently, never forwarded
 * X-Aetheris-* headers, pod IP, localhost access, or a synthetic service header.
 * Administrative APIs are single-domain, not tenant-aware: until a real
 * owner/tenant model exists, they are ADMIN-only.
 *
 * Signed issuer/audience claims are independently verified in this draft.
 * Shared-key rotation, revocation and workload identity remain open (#251 / #229).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public final class DirectApiJwtFilter extends OncePerRequestFilter {
    public static final String DEFAULT_ISSUER = "aetheris-identity";
    public static final String DEFAULT_AUDIENCE = "aetheris-api";
    private final SecretKey key;
    private final PublicKey rsaPublicKey;
    private final PublicKey rsaPreviousPublicKey;
    private final String rsaPreviousKid;
    private final Instant previousKeyNotAfter;
    private final Clock clock;
    private static final long MAX_PREVIOUS_KEY_GRACE_SECONDS = 3600L;
    // Fixed cap applies equally to HMAC and RS256.
    private static final long MAX_ACCESS_TOKEN_LIFETIME_SECONDS = 3600L;
    private final String rsaKid;
    private final String mode;
    private final String issuer;
    private final String audience;
    // Review-only B62-G5 seam: production Autowired constructor always passes null.
    // No external property, Spring bean or JDBC endpoint can activate this.
    @FunctionalInterface
    interface TestOnlyRevocationDecision {
        boolean allowed(long verifiedUid, String verifiedIssuer, String verifiedKid,
                        long signedIssuedAtEpoch) throws Exception;
    }
    private final TestOnlyRevocationDecision b62g5TestOnlyDecision;
    private static final String ROOT = "/api/events";

    @Autowired
    public DirectApiJwtFilter(@Value("${aetheris.jwt.secret:}") String secret,
            @Value("${aetheris.jwt.issuer:aetheris-identity}") String issuer,
            @Value("${aetheris.jwt.audience:aetheris-api}") String audience,
            @Value("${aetheris.jwt.mode:hmac}") String mode,
            @Value("${aetheris.jwt.rsa-public-key-x509-base64:}") String publicKey,
            @Value("${aetheris.jwt.kid:}") String kid,
            @Value("${aetheris.jwt.rsa-previous-public-key-x509-base64:}") String previousPublicKey,
            @Value("${aetheris.jwt.previous-kid:}") String previousKid,
            @Value("${aetheris.jwt.previous-key-not-after-epoch-seconds:}") String previousNotAfter) {
        this(secret, issuer, audience, mode, publicKey, kid, previousPublicKey,
            previousKid, previousNotAfter, Clock.systemUTC());
    }

    // Explicit injectable clock for deterministic deadline regressions; production
    // injects the real UTC clock, never a request-controlled timestamp.
    public DirectApiJwtFilter(String secret, String issuer, String audience, String mode,
            String publicKey, String kid, String previousPublicKey,
            String previousKid, String previousNotAfter, Clock clock) {
        this(secret, issuer, audience, mode, publicKey, kid, previousPublicKey,
            previousKid, previousNotAfter, clock, null);
    }

    // Package-private test-only construction; never an injectable Spring or
    // configuration path. The normal production filter remains unchanged.
    DirectApiJwtFilter(String secret, String issuer, String audience, String mode,
            String publicKey, String kid, String previousPublicKey,
            String previousKid, String previousNotAfter, Clock clock,
            TestOnlyRevocationDecision injectedDecision) {
        if (injectedDecision != null && !"rs256".equals(mode)) {
            throw new IllegalArgumentException("Revocation proof requires RS256 signing");
        }
        this.b62g5TestOnlyDecision = injectedDecision;
        this.clock = java.util.Objects.requireNonNull(clock, "clock");
        if (issuer == null || issuer.isBlank() || audience == null || audience.isBlank()) {
            throw new IllegalArgumentException("JWT issuer and audience must be configured");
        }
        this.issuer = issuer;
        this.audience = audience;
        this.mode = mode;
        if ("hmac".equals(mode)) {
            if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32
                    || (publicKey != null && !publicKey.isBlank())
                    || (kid != null && !kid.isBlank())
                    || (previousPublicKey != null && !previousPublicKey.isBlank())
                    || (previousKid != null && !previousKid.isBlank())
                    || (previousNotAfter != null && !previousNotAfter.isBlank())) {
                throw new IllegalArgumentException("Invalid HMAC JWT verifier configuration");
            }
            this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
            this.rsaPublicKey = null;
            this.rsaKid = null;
            this.rsaPreviousPublicKey = null;
            this.rsaPreviousKid = null;
            this.previousKeyNotAfter = null;
        } else if ("rs256".equals(mode)) {
            if ((secret != null && !secret.isBlank()) || publicKey == null
                    || publicKey.isBlank() || publicKey.length() > 16384
                    || kid == null || !kid.matches("[a-z][a-z0-9-]{0,30}")
                    || ((previousPublicKey == null || previousPublicKey.isBlank())
                          != (previousKid == null || previousKid.isBlank()))) {
                throw new IllegalArgumentException("Invalid RS256 JWT verifier configuration");
            }
            try {
                byte[] der = Base64.getDecoder().decode(publicKey);
                PublicKey parsed = KeyFactory.getInstance("RSA")
                        .generatePublic(new X509EncodedKeySpec(der));
                if (!(parsed instanceof RSAPublicKey rsa) || rsa.getModulus().bitLength() < 3072) {
                    throw new IllegalArgumentException("RSA verifier key too weak");
                }
                this.rsaPublicKey = parsed;
            } catch (Exception invalidKey) {
                throw new IllegalArgumentException("Invalid RSA public verification key");
            }
            this.rsaKid = kid;
            if (previousKid != null && !previousKid.isBlank()) {
                if (!previousKid.matches("[a-z][a-z0-9-]{0,30}") || previousKid.equals(kid)
                        || previousPublicKey.length() > 16384) {
                    throw new IllegalArgumentException("Invalid RS256 previous-key configuration");
                }
                try {
                    byte[] previousDer = Base64.getDecoder().decode(previousPublicKey);
                    PublicKey candidate = KeyFactory.getInstance("RSA")
                        .generatePublic(new X509EncodedKeySpec(previousDer));
                    if (!(candidate instanceof RSAPublicKey previousRsa)
                            || previousRsa.getModulus().bitLength() < 3072
                            || java.util.Arrays.equals(candidate.getEncoded(), this.rsaPublicKey.getEncoded())) {
                        throw new IllegalArgumentException("Previous RSA verification key invalid or duplicated");
                    }
                    this.rsaPreviousPublicKey = candidate;
                } catch (Exception invalidPreviousKey) {
                    throw new IllegalArgumentException("Invalid previous RSA verification key");
                }
                this.rsaPreviousKid = previousKid;
                if (previousNotAfter == null || !previousNotAfter.matches("[1-9][0-9]{9}")) {
                    throw new IllegalArgumentException("Previous public key requires strict UTC epoch cutoff");
                }
                final Instant cutoff;
                try {
                    cutoff = Instant.ofEpochSecond(Long.parseLong(previousNotAfter));
                } catch (RuntimeException invalidDeadline) {
                    throw new IllegalArgumentException("Invalid previous public key cutoff");
                }
                // Expired cutoffs are allowed at startup so current credentials
                // remain usable, but the previous key must never authenticate.
                if (cutoff.isAfter(clock.instant().plusSeconds(MAX_PREVIOUS_KEY_GRACE_SECONDS))) {
                    throw new IllegalArgumentException("Previous public key trust exceeds maximum overlap");
                }
                this.previousKeyNotAfter = cutoff;
            } else {
                if (previousNotAfter != null && !previousNotAfter.isBlank()) {
                    throw new IllegalArgumentException("Cutoff without previous public key");
                }
                this.rsaPreviousPublicKey = null;
                this.rsaPreviousKid = null;
                this.previousKeyNotAfter = null;
            }
            this.key = null;
        } else {
            throw new IllegalArgumentException("Unsupported JWT verification mode");
        }
    }

    // Existing HMAC contracts are preserved until reviewed coordinated rollout.
    // B59 constructor retained for explicit single-key verification and existing contract tests.
    public DirectApiJwtFilter(String secret, String issuer, String audience,
                     String mode, String publicKey, String kid) {
        this(secret, issuer, audience, mode, publicKey, kid, "", "", "");
    }

    // Source-compatible B60 helper: refuses previous-key enrollment without
    // an explicit deadline, rather than accidentally trusting it forever.
    public DirectApiJwtFilter(String secret, String issuer, String audience,
                     String mode, String publicKey, String kid,
                     String previousPublicKey, String previousKid) {
        this(secret, issuer, audience, mode, publicKey, kid, previousPublicKey, previousKid, "");
    }

    public DirectApiJwtFilter(String secret, String issuer, String audience) {
        this(secret, issuer, audience, "hmac", "", "", "", "", "");
    }

    public DirectApiJwtFilter(String secret) {
        this(secret, DEFAULT_ISSUER, DEFAULT_AUDIENCE);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // URI minus the container context path works for standalone MockMvc, root
        // Servlet mappings, and non-root deployments. Never trust forwarded URI headers.
        String uri = request.getRequestURI();
        String context = request.getContextPath();
        String path = uri.startsWith(context) ? uri.substring(context.length()) : uri;
        return !(ROOT.equals(path) || path.startsWith(ROOT + "/"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        List<String> headers = java.util.Collections.list(request.getHeaders(HttpHeaders.AUTHORIZATION));
        if (headers.size() != 1 || headers.get(0) == null
                || headers.get(0).length() > 4096 || !headers.get(0).startsWith("Bearer ")) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Bearer authorization required");
            return;
        }

        final Claims claims;
        final String verifiedSignerKid;
        try {
            String token = headers.get(0).substring("Bearer ".length());
            if (token.isBlank() || token.contains(" ") || token.contains("\t")) {
                throw new IllegalArgumentException("Malformed bearer token");
            }
            io.jsonwebtoken.Jws<Claims> signed;
            boolean previousKeyVerified = false;
            if ("rs256".equals(mode)) {
                // Never consult kid-selected URLs. Verify only pinned public keys,
                // then enforce the signed kid matches the key that actually verified.
                try {
                    signed = Jwts.parser().clock(() -> Date.from(clock.instant()))
                        .clockSkewSeconds(0).verifyWith(rsaPublicKey)
                        .requireIssuer(issuer).requireAudience(audience)
                        .build().parseSignedClaims(token);
                } catch (io.jsonwebtoken.JwtException invalidCurrent) {
                    if (rsaPreviousPublicKey == null
                            || !clock.instant().isBefore(previousKeyNotAfter)) throw invalidCurrent;
                    signed = Jwts.parser().clock(() -> Date.from(clock.instant()))
                        .clockSkewSeconds(0).verifyWith(rsaPreviousPublicKey)
                        .requireIssuer(issuer).requireAudience(audience)
                        .build().parseSignedClaims(token);
                    previousKeyVerified = true;
                }
            } else {
                signed = Jwts.parser().clock(() -> Date.from(clock.instant()))
                        .clockSkewSeconds(0).verifyWith(key).requireIssuer(issuer)
                    .requireAudience(audience).build().parseSignedClaims(token);
            }
            if ("rs256".equals(mode) && (!"RS256".equals(signed.getHeader().getAlgorithm())
                    || (previousKeyVerified ? !rsaPreviousKid.equals(signed.getHeader().getKeyId())
                                            : !rsaKid.equals(signed.getHeader().getKeyId())))) {
                throw new SecurityException("Rejected JWT signing key identity");
            }
            if (previousKeyVerified && !clock.instant().isBefore(previousKeyNotAfter)) {
                throw new SecurityException("Previous public signing key expired");
            }
            verifiedSignerKid = signed.getHeader().getKeyId();
            claims = signed.getPayload();
            // requireAudience() checks membership; reject cross-workload multi-audience tokens.
            if (!java.util.Set.of(audience).equals(claims.getAudience())) {
                throw new IllegalArgumentException("Ambiguous bearer token audience");
            }
            Date expires = claims.getExpiration();
            Date issued = claims.getIssuedAt();
            // B62-A: evaluate claim times using the same clock as key retirement.
            Instant requestNow = clock.instant();
            String subject = claims.getSubject();
            Object uid = claims.get("uid");
            if (expires == null || issued == null
                || !expires.toInstant().isAfter(issued.toInstant())
                || java.time.Duration.between(issued.toInstant(), expires.toInstant())
                     .compareTo(java.time.Duration.ofSeconds(MAX_ACCESS_TOKEN_LIFETIME_SECONDS)) > 0
                || !expires.toInstant().isAfter(requestNow)
                || issued.toInstant().isAfter(requestNow.plusSeconds(30))
                || subject == null || subject.isBlank()
                || !isPositiveIntegralUid(uid)) {
                throw new IllegalArgumentException("Required verified JWT claims missing");
            }
        } catch (Exception exception) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid bearer token");
            return;
        }

        Object role = claims.get("role");
        Object scopes = claims.get("scopes");
        boolean administrator = Objects.equals(role, "ADMIN");
        boolean authorizedScope = scopes instanceof Collection<?> collection
            && collection.stream().allMatch(String.class::isInstance)
            && collection.contains(requiredScope(request));
        if (!administrator || !authorizedScope) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Insufficient permission");
            return;
        }
        // B62-G5 test-only reference: even a correctly signed and scoped JWT
        // is rejected when emergency state denies or the decision store fails.
        // The existing Spring constructor installs NULL: no deployed hookup.
        if (b62g5TestOnlyDecision != null) {
            Object uidClaim = claims.get("uid");
            if (!isPositiveIntegralUid(uidClaim)) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Revocation identity invalid");
                return;
            }
            long signedUid = uidClaim instanceof Integer value
                    ? value.longValue() : ((Long) uidClaim).longValue();
            try {
                if (!b62g5TestOnlyDecision.allowed(signedUid, issuer,
                        verifiedSignerKid, claims.getIssuedAt().toInstant().getEpochSecond())) {
                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Bearer token rejected");
                    return;
                }
            } catch (Exception storeUnavailableOrTimeout) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Bearer token rejected");
                return;
            }
        }
        chain.doFilter(request, response);
    }

    /**
     * JSON numbers are not interchangeable identities. Parsing uid=42.5 by
     * longValue() silently authenticates it as 42; very large numbers can
     * overflow. The Identity issuer writes a Java Long, decoded as Integer
     * for small JSON integers and Long for large ones.
     */
    private static boolean isPositiveIntegralUid(Object uid) {
        return (uid instanceof Integer integerId && integerId > 0)
            || (uid instanceof Long longId && longId > 0L);
    }

    private String requiredScope(HttpServletRequest request) {
        return ("GET".equals(request.getMethod()) || "HEAD".equals(request.getMethod()))
            ? "services:read" : "services:write";
    }
}
