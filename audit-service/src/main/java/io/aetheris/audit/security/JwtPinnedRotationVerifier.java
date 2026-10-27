package io.aetheris.audit.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.JwtException;

import java.security.PublicKey;
import java.util.Arrays;

/**
 * An operator-pinned, at-most-two RSA verifier; never downloads keys or
 * resolves JWT kid to a URL. Signature is verified before kid is trusted.
 */
final class JwtPinnedRotationVerifier {
    private final PublicKey active;
    private final PublicKey staged;
    private final String activeKid;
    private final String stagedKid;

    JwtPinnedRotationVerifier(String activePem, String activeId,
                               String stagedPem, String stagedId) {
        boolean hasActive = filled(activePem);
        boolean hasActiveId = filled(activeId);
        boolean hasStaged = filled(stagedPem);
        boolean hasStagedId = filled(stagedId);
        if (!hasActive && (hasActiveId || hasStaged || hasStagedId)) {
            throw new IllegalArgumentException("RS256 rotation requires active public key");
        }
        if (hasStaged != hasStagedId || (hasStaged && !hasActiveId)) {
            throw new IllegalArgumentException("RS256 rotation requires both pinned key IDs");
        }
        if (hasActiveId && !validKid(activeId)) {
            throw new IllegalArgumentException("Invalid active RS256 key ID");
        }
        if (hasStagedId && !validKid(stagedId)) {
            throw new IllegalArgumentException("Invalid staged RS256 key ID");
        }
        if (hasStaged && activeId.equals(stagedId)) {
            throw new IllegalArgumentException("RS256 rotation key IDs must be distinct");
        }
        this.active = hasActive ? JwtRs256KeySupport.publicKey(activePem) : null;
        this.staged = hasStaged ? JwtRs256KeySupport.publicKey(stagedPem) : null;
        if (staged != null && Arrays.equals(active.getEncoded(), staged.getEncoded())) {
            throw new IllegalArgumentException("RS256 staged key must differ");
        }
        this.activeKid = hasActiveId ? activeId : null;
        this.stagedKid = hasStagedId ? stagedId : null;
    }

    boolean configured() { return active != null; }

    /**
     * Never select a key based on a still-unverified JWT header. Verify only
     * against the two preconfigured keys, then match signed header kid with
     * the key actually used. Remove a key from config to revoke acceptance.
     */
    Jws<Claims> verify(String jwt) {
        if (active == null) throw new JwtException("RS256 verifier is not configured");
        Jws<Claims> first = tryVerify(jwt, active, activeKid);
        if (first != null) return first;
        if (staged != null) {
            Jws<Claims> second = tryVerify(jwt, staged, stagedKid);
            if (second != null) return second;
        }
        throw new JwtException("RS256 signature or pinned key ID rejected");
    }

    private static Jws<Claims> tryVerify(String jwt, PublicKey key, String expectedKid) {
        try {
            Jws<Claims> signed = Jwts.parser().verifyWith(key).build().parseSignedClaims(jwt);
            if (!"RS256".equals(signed.getHeader().getAlgorithm())) return null;
            if (expectedKid != null && !expectedKid.equals(signed.getHeader().getKeyId())) return null;
            // Without a configured key ID, the one-key RS256 candidate remains
            // backward-compatible. Two-key rotation REQUIRES IDs for both.
            return signed;
        } catch (JwtException | IllegalArgumentException invalid) {
            return null;
        }
    }

    static boolean validKid(String id) {
        return id != null && id.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,63}");
    }

    private static boolean filled(String value) {
        return value != null && !value.isEmpty();
    }
}
