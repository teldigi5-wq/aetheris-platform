package io.aetheris.gateway;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.Clock;
import java.util.Date;
import java.util.List;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

@Component
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {
    static final String VERIFIED_USER_ID_ATTRIBUTE = "aetheris.gateway.verifiedUserId";
    private static final Set<HttpMethod> WRITE_METHODS = Set.of(HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH, HttpMethod.DELETE);
    public static final String DEFAULT_ISSUER = "aetheris-identity";
    public static final String DEFAULT_AUDIENCE = "aetheris-api";
    private final SecretKey key;
    private final PublicKey rsaPublicKey;
    private final PublicKey rsaPreviousPublicKey;
    private final String rsaPreviousKid;
    private final Instant previousKeyNotAfter;
    private final Clock clock;
    private static final long MAX_PREVIOUS_KEY_GRACE_SECONDS = 3600L;
    private final String rsaKid;
    private final String mode;
    private final String issuer;
    private final String audience;

    @Autowired
    public JwtAuthenticationFilter(@Value("${aetheris.jwt.secret:}") String secret,
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
    public JwtAuthenticationFilter(String secret, String issuer, String audience, String mode,
            String publicKey, String kid, String previousPublicKey,
            String previousKid, String previousNotAfter, Clock clock) {
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
    public JwtAuthenticationFilter(String secret, String issuer, String audience,
                     String mode, String publicKey, String kid) {
        this(secret, issuer, audience, mode, publicKey, kid, "", "", "");
    }

    // Source-compatible B60 helper: refuses previous-key enrollment without
    // an explicit deadline, rather than accidentally trusting it forever.
    public JwtAuthenticationFilter(String secret, String issuer, String audience,
                     String mode, String publicKey, String kid,
                     String previousPublicKey, String previousKid) {
        this(secret, issuer, audience, mode, publicKey, kid, previousPublicKey, previousKid, "");
    }

    public JwtAuthenticationFilter(String secret, String issuer, String audience,
                     String mode, String publicKey, String kid,
                     String previousPublicKey, String previousKid, String previousNotAfter) {
        this(secret, issuer, audience, mode, publicKey, kid,
             previousPublicKey, previousKid, previousNotAfter, Clock.systemUTC());
    }

    public JwtAuthenticationFilter(String secret, String issuer, String audience) {
        this(secret, issuer, audience, "hmac", "", "", "", "", "");
    }

    public JwtAuthenticationFilter(String secret) {
        this(secret, DEFAULT_ISSUER, DEFAULT_AUDIENCE);
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        if (!isProtectedRoute(path)) {
            return chain.filter(exchange);
        }

        List<String> authorizationHeaders = exchange.getRequest().getHeaders().get(HttpHeaders.AUTHORIZATION);
        if (authorizationHeaders == null || authorizationHeaders.size() != 1
                || authorizationHeaders.get(0) == null
                || authorizationHeaders.get(0).length() > 4096
                || !authorizationHeaders.get(0).startsWith("Bearer ")) {
            return error(exchange, HttpStatus.UNAUTHORIZED, "Unauthorized", "Exactly one bearer token required");
        }

        String token = authorizationHeaders.get(0).substring(7);
        if (token.isBlank() || token.contains(" ") || token.contains("\t")) {
            return error(exchange, HttpStatus.UNAUTHORIZED, "Unauthorized", "Malformed bearer token");
        }
        try {
            io.jsonwebtoken.Jws<Claims> signed;
            boolean previousKeyVerified = false;
            if ("rs256".equals(mode)) {
                // Never trust an unverified kid to load a key/URL. Try only the two
                // explicit, strong allowlisted public keys, then bind the signed kid.
                try {
                    signed = Jwts.parser().verifyWith(rsaPublicKey)
                        .requireIssuer(issuer).requireAudience(audience)
                        .build().parseSignedClaims(token);
                } catch (io.jsonwebtoken.JwtException invalidCurrent) {
                    if (rsaPreviousPublicKey == null
                            || !clock.instant().isBefore(previousKeyNotAfter)) throw invalidCurrent;
                    signed = Jwts.parser().verifyWith(rsaPreviousPublicKey)
                        .requireIssuer(issuer).requireAudience(audience)
                        .build().parseSignedClaims(token);
                    previousKeyVerified = true;
                }
            } else {
                signed = Jwts.parser().verifyWith(key).requireIssuer(issuer)
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
            Claims claims = signed.getPayload();

            // An audience collection that also names another workload is NOT an API credential.
            if (!Set.of(audience).equals(claims.getAudience())) {
                return error(exchange, HttpStatus.UNAUTHORIZED, "Unauthorized", "Invalid access token audience");
            }

            String email = claims.getSubject();
            String role = claims.get("role", String.class);
            Object userId = claims.get("uid");
            Set<String> scopes = readScopes(claims.get("scopes"));

            if (email == null || email.isBlank() || role == null || role.isBlank()) {
                return error(exchange, HttpStatus.UNAUTHORIZED, "Unauthorized", "Token is missing required claims");
            }

            // User and Audit are currently single-domain ADMIN-only APIs.
            // Mirror their own direct-service checks at the gateway, while
            // leaving Orchestrator's distinct role/scopes policy unchanged.
            if (isAdministrativeApi(path)) {
                Date issuedAt = claims.getIssuedAt();
                Date expiration = claims.getExpiration();
                if (!isPositiveIntegralUid(userId) || issuedAt == null
                        || issuedAt.toInstant().isAfter(Instant.now().plusSeconds(30))
                        || expiration == null || !expiration.toInstant().isAfter(Instant.now())) {
                    return error(exchange, HttpStatus.UNAUTHORIZED, "Unauthorized",
                            "Invalid administrative token identity or lifetime");
                }
                if (!"ADMIN".equals(role)) {
                    return error(exchange, HttpStatus.FORBIDDEN, "Forbidden",
                            "Administrative role required");
                }
            }

            String requiredScope = requiredScope(path, exchange.getRequest().getMethod());
            if (requiredScope != null && !scopes.contains(requiredScope)) {
                return error(exchange, HttpStatus.FORBIDDEN, "Forbidden", "Missing required scope: " + requiredScope);
            }

            // This attribute is never copied from request headers. Only signed,
            // in-scope tokens are permitted to select a per-user limiter bucket.
            if (isPositiveIntegralUid(userId)) {
                exchange.getAttributes().put(VERIFIED_USER_ID_ATTRIBUTE, String.valueOf(userId));
            }

            ServerWebExchange authenticatedExchange = exchange.mutate()
                    .request(builder -> builder.headers(headers -> {
                        headers.remove("X-Aetheris-User-Id");
                        headers.remove("X-Aetheris-User-Email");
                        headers.remove("X-Aetheris-User-Role");
                        headers.remove("X-Aetheris-Scopes");
                        headers.set("X-Aetheris-User-Email", email);
                        headers.set("X-Aetheris-User-Role", role);
                        headers.set("X-Aetheris-Scopes", String.join(" ", scopes.stream().sorted().toList()));
                        if (isPositiveIntegralUid(userId)) {
                            headers.set("X-Aetheris-User-Id", String.valueOf(userId));
                        }
                    }))
                    .build();

            return chain.filter(authenticatedExchange);
        } catch (Exception exception) {
            return error(exchange, HttpStatus.UNAUTHORIZED, "Unauthorized", "Invalid or expired access token");
        }
    }

    private static boolean isPositiveIntegralUid(Object uid) {
        return (uid instanceof Integer integerId && integerId > 0)
                || (uid instanceof Long longId && longId > 0L);
    }

    private static boolean isAdministrativeApi(String path) {
        return path.equals("/api/users") || path.startsWith("/api/users/")
                || path.equals("/api/events") || path.startsWith("/api/events/");
    }

    private Set<String> readScopes(Object claim) {
        Set<String> scopes = new HashSet<>();
        if (claim instanceof Collection<?> values) {
            values.stream().filter(String.class::isInstance).map(String.class::cast).forEach(scopes::add);
        } else if (claim instanceof String value) {
            for (String scope : value.split("\\s+")) {
                if (!scope.isBlank()) scopes.add(scope);
            }
        }
        return scopes;
    }

    private boolean isProtectedRoute(String path) {
        return path.equals("/api/users") || path.startsWith("/api/users/")
                || path.equals("/api/events") || path.startsWith("/api/events/")
                || path.equals("/api/orchestrator") || path.startsWith("/api/orchestrator/");
    }

    private String requiredScope(String path, HttpMethod method) {
        if (path.equals("/api/events") || path.startsWith("/api/events/")) {
            // Keep the independent Audit filter's HTTP-method scope contract.
            // Identity currently grants no services:write; never treat a
            // read-only ADMIN token as authority to modify audit evidence.
            return method != null && WRITE_METHODS.contains(method)
                    ? "services:write" : "services:read";
        }
        if (path.equals("/api/orchestrator") || path.startsWith("/api/orchestrator/")) {
            return method != null && WRITE_METHODS.contains(method) ? "orchestrator:write" : "orchestrator:read";
        }
        return method != null && WRITE_METHODS.contains(method) ? "users:write" : "users:read";
    }

    private Mono<Void> error(ServerWebExchange exchange, HttpStatus status, String error, String message) {
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String escapedMessage = message.replace("\\", "\\\\").replace("\"", "\\\"");
        byte[] bytes = ("{\"status\":" + status.value() + ",\"error\":\"" + error + "\",\"message\":\"" + escapedMessage + "\"}")
                .getBytes(StandardCharsets.UTF_8);
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return -100;
    }
}
