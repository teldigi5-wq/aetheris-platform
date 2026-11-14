package io.aetheris.gateway;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Set;

/**
 * Fail-closed validation for the HMAC JWT secret (ported from closed #423 onto the
 * B62-F integration line, I7). Duplicated in gateway, identity, user and audit on
 * purpose: the modules share no library, and every HMAC signer/verifier must refuse
 * to start with the same weak or published values.
 */
final class JwtSecretPolicy {
    static final int MIN_LENGTH = 32;
    static final int MIN_DISTINCT_CHARACTERS = 10;

    /**
     * SHA-256 of secrets that appear in this public repository's current or historical
     * source: the former compose/application.yml default and the former gateway unit-test
     * fixture. Stored as hashes so no signing-secret literal is reintroduced.
     */
    static final Set<String> PUBLISHED_SECRET_SHA256 = Set.of(
            "22e640fc1be89888f29ca3856764204697f97f50cf5b94bfb91b685542ebd6ff",
            "3eb1bd439947eb762998e566ccc2e099c791118b2f40579cc4f7da2b5061b7f9");

    private JwtSecretPolicy() {
    }

    static String requireStrong(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("AETHERIS_JWT_SECRET must be set; there is no default signing secret");
        }
        if (secret.length() < MIN_LENGTH) {
            throw new IllegalArgumentException("JWT secret must be at least " + MIN_LENGTH + " characters");
        }
        if (isPublished(secret)) {
            throw new IllegalArgumentException("JWT secret is a publicly published value; generate a new one (e.g. openssl rand -hex 32)");
        }
        if (secret.chars().distinct().count() < MIN_DISTINCT_CHARACTERS) {
            throw new IllegalArgumentException("JWT secret is too repetitive; generate a random one (e.g. openssl rand -hex 32)");
        }
        return secret;
    }

    static boolean isPublished(String secret) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(secret.trim().getBytes(StandardCharsets.UTF_8));
            return PUBLISHED_SECRET_SHA256.contains(HexFormat.of().formatHex(digest));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }
}
