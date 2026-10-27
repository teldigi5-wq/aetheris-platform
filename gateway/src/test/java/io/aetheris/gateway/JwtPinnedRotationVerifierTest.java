package io.aetheris.gateway;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

/** Real signature verification with throwaway keys, no network or shared store. */
class JwtPinnedRotationVerifierTest {
    private KeyPair pair() throws Exception {
        KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
        g.initialize(2048);
        return g.generateKeyPair();
    }

    private String pem(KeyPair keys) {
        return "-----BEGIN PUBLIC KEY-----\n"
            + Base64.getMimeEncoder(64, new byte[]{'\n'})
                .encodeToString(keys.getPublic().getEncoded())
            + "\n-----END PUBLIC KEY-----";
    }

    private String signed(KeyPair key, String id) {
        Instant now = Instant.now();
        var b = Jwts.builder().subject("rotation@example.invalid")
            .issuedAt(Date.from(now.minusSeconds(5)))
            .expiration(Date.from(now.plusSeconds(120)));
        if (id != null) b.header().keyId(id).and();
        return b.signWith(key.getPrivate(), Jwts.SIG.RS256).compact();
    }

    @Test
    void overlapAdmitsOnlyMatchingVerifiedActiveOrStagedKid() throws Exception {
        KeyPair active = pair(), staged = pair(), wrong = pair();
        var verifier = new JwtPinnedRotationVerifier(
            pem(active), "release-001", pem(staged), "release-002");
        assertEquals("release-001", verifier.verify(
            signed(active, "release-001")).getHeader().getKeyId());
        assertEquals("release-002", verifier.verify(
            signed(staged, "release-002")).getHeader().getKeyId());
        assertThrows(Exception.class, () -> verifier.verify(signed(active, "release-002")));
        assertThrows(Exception.class, () -> verifier.verify(signed(staged, "release-001")));
        assertThrows(Exception.class, () -> verifier.verify(signed(staged, "unknown")));
        assertThrows(Exception.class, () -> verifier.verify(signed(staged, null)));
        assertThrows(Exception.class, () -> verifier.verify(signed(wrong, "release-002")));
    }

    @Test
    void retiringOldKeyImmediatelyRejectsOldSignatures() throws Exception {
        KeyPair oldKey = pair(), newKey = pair();
        var overlap = new JwtPinnedRotationVerifier(
            pem(oldKey), "old", pem(newKey), "new");
        assertNotNull(overlap.verify(signed(oldKey, "old")));
        assertNotNull(overlap.verify(signed(newKey, "new")));
        var retired = new JwtPinnedRotationVerifier(pem(newKey), "new", "", "");
        assertThrows(Exception.class, () -> retired.verify(signed(oldKey, "old")));
        assertNotNull(retired.verify(signed(newKey, "new")));
        // Even a properly signed HMAC token cannot downgrade an RSA verifier.
        var hs = Jwts.builder().subject("rotation@example.invalid")
            .signWith(Keys.hmacShaKeyFor(
                "synthetic-hmac-not-used-as-rsa-key-20261027-and-long-enough"
                    .getBytes(StandardCharsets.UTF_8))).compact();
        assertThrows(Exception.class, () -> retired.verify(hs));
    }

    @Test
    void partialDuplicateAndMalformedRotationSettingsFailClosed() throws Exception {
        KeyPair first = pair(), second = pair();
        assertThrows(IllegalArgumentException.class,
            () -> new JwtPinnedRotationVerifier("", "active", "", ""));
        assertThrows(IllegalArgumentException.class,
            () -> new JwtPinnedRotationVerifier("", "", pem(second), "new"));
        assertThrows(IllegalArgumentException.class,
            () -> new JwtPinnedRotationVerifier(pem(first), "", pem(second), "new"));
        assertThrows(IllegalArgumentException.class,
            () -> new JwtPinnedRotationVerifier(pem(first), "old", pem(second), ""));
        assertThrows(IllegalArgumentException.class,
            () -> new JwtPinnedRotationVerifier(pem(first), "old", "", "new"));
        assertThrows(IllegalArgumentException.class,
            () -> new JwtPinnedRotationVerifier(pem(first), "same", pem(second), "same"));
        assertThrows(IllegalArgumentException.class,
            () -> new JwtPinnedRotationVerifier(pem(first), "old", pem(first), "same-key"));
        assertThrows(IllegalArgumentException.class,
            () -> new JwtPinnedRotationVerifier(pem(first), "../wrong", "", ""));
        assertThrows(IllegalArgumentException.class,
            () -> new JwtPinnedRotationVerifier(pem(first), " ", "", ""));
        assertThrows(IllegalArgumentException.class,
            () -> new JwtPinnedRotationVerifier(pem(first), "", "invalid-pem", "new"));
    }
}
