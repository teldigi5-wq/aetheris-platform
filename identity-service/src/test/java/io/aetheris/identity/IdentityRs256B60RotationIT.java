package io.aetheris.identity;

import org.junit.jupiter.api.Test;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.Jwts;
import org.springframework.test.util.ReflectionTestUtils;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Date;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Purely ephemeral Identity-owned signing material for the two-key B60
 * integration/HTTP tests. Writes PUBLIC DER plus test JWT only to target/.
 */
class IdentityRs256B60RotationIT {
    static final String CURRENT = "b60-current";
    static final String PREVIOUS = "b60-previous";

    private static KeyPair generate() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(3072);
        return generator.generateKeyPair();
    }

    private static String issue(KeyPair key, String kid, String iss, String aud) {
        return issueWithLifetime(key, kid, iss, aud, 1200);
    }

    private static String issueWithLifetime(KeyPair key, String kid, String iss, String aud,
                                            long lifetimeSeconds) {
        var account = new IdentityAccount("B60 Synthetic", "b60-admin@example.invalid",
            "no-real-password", Role.ADMIN);
        ReflectionTestUtils.setField(account, "id", 42L);
        // B61 CI runs full HMAC/RSA and a real UTC deadline wait before
        // post-cutoff assertions. This TEST-ONLY JWT must outlast that run;
        // it is never sent to production or a real account.
        // Invalid signer-validity examples are intentionally signed by a
        // TEST-ONLY JJWT builder; production JwtService rejects these settings.
        if (lifetimeSeconds < 1 || lifetimeSeconds > JwtService.MAX_ACCESS_TOKEN_LIFETIME_SECONDS) {
            Instant now = Instant.now();
            return Jwts.builder()
                .issuer(iss).audience().add(aud).and()
                .subject(account.getEmail())
                .claim("uid", account.getId())
                .claim("name", account.getName())
                .claim("role", account.getRole().name())
                .claim("scopes", account.getRole().scopes().stream().sorted().toList())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(lifetimeSeconds)))
                .header().keyId(kid).and()
                .signWith(key.getPrivate(), Jwts.SIG.RS256).compact();
        }
        return new JwtService("", lifetimeSeconds, iss, aud, "rs256",
            Base64.getEncoder().encodeToString(key.getPrivate().getEncoded()), kid)
            .issue(account);
    }

    @Test
    void generateOnlyEphemeralNewOldPublicTrustBundleAndSignedTestTokens() throws Exception {
        KeyPair current = generate(), previous = generate(), untrusted = generate();
        Path out = Path.of("target","b60-rotation").toAbsolutePath().normalize();
        assertTrue(out.startsWith(Path.of("target").toAbsolutePath().normalize()));
        Files.createDirectories(out);
        String currentPublic=Base64.getEncoder().encodeToString(current.getPublic().getEncoded());
        String previousPublic=Base64.getEncoder().encodeToString(previous.getPublic().getEncoded());
        assertNotEquals(currentPublic,previousPublic);
        Files.writeString(out.resolve("current-public-x509.b64"),currentPublic,StandardCharsets.US_ASCII);
        Files.writeString(out.resolve("previous-public-x509.b64"),previousPublic,StandardCharsets.US_ASCII);
        // Deliberate JJWT-signed nbf credential: parser's clock, not a
        // request-controlled clock or unsigned payload, decides validity.
        Instant signedAt = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        Instant notBefore = signedAt.plusSeconds(300);
        String futureNotBefore = Jwts.builder()
            .issuer("aetheris-identity").audience().add("aetheris-api").and()
            .subject("b60-admin@example.invalid")
            .claim("uid", 42L).claim("role", "ADMIN")
            .claim("scopes", Role.ADMIN.scopes().stream().sorted().toList())
            .issuedAt(Date.from(signedAt))
            .notBefore(Date.from(notBefore))
            .expiration(Date.from(signedAt.plusSeconds(1200)))
            .header().keyId(CURRENT).and()
            .signWith(current.getPrivate(), Jwts.SIG.RS256).compact();
        var tokens=new java.util.HashMap<String,String>(Map.of(
            "current",issue(current,CURRENT,"aetheris-identity","aetheris-api"),
            "previous",issue(previous,PREVIOUS,"aetheris-identity","aetheris-api"),
            // Signed with OLD actual key, labeled NEW kid: must fail.
            "relabel-previous",issue(previous,CURRENT,"aetheris-identity","aetheris-api"),
            "unknown-kid",issue(current,"unknown-b60","aetheris-identity","aetheris-api"),
            "untrusted-key",issue(untrusted,CURRENT,"aetheris-identity","aetheris-api"),
            "foreign-issuer",issue(current,CURRENT,"foreign-issuer","aetheris-api"),
            "workload-only",issue(current,CURRENT,"aetheris-identity","aetheris-runtime"),
            // Extra B62-B real Identity-signed synthetic JWTs.
            "max-lifetime",issueWithLifetime(current,CURRENT,"aetheris-identity","aetheris-api",3600),
            "overlong-lifetime",issueWithLifetime(current,CURRENT,"aetheris-identity","aetheris-api",3601),
            "zero-lifetime",issueWithLifetime(current,CURRENT,"aetheris-identity","aetheris-api",0)
        ));
        tokens.put("future-nbf", futureNotBefore);
        Files.writeString(out.resolve("future-nbf.epoch"),
            Long.toString(notBefore.getEpochSecond()), StandardCharsets.US_ASCII);
        for(var entry:tokens.entrySet()){
            assertEquals(3,entry.getValue().split("\\.").length);
            Files.writeString(out.resolve(entry.getKey()+".jwt"),entry.getValue(),StandardCharsets.US_ASCII);
        }
        Files.writeString(out.resolve("test-only.marker"),"B60_EPHEMERAL_PUBLIC_ROLLOVER_V1\n",
            StandardCharsets.US_ASCII);
        assertFalse(Files.exists(out.resolve("private")));
        System.out.println("B60_EPHEMERAL_IDENTITY_CURRENT_PREVIOUS_SIGNED_FIXTURES_READY");
    }
}
