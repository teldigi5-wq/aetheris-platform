package io.aetheris.identity;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Explicit B59 CI contract integration (not in normal Surefire reactor).
 * No production secret or private key file is created. Only public DER and
 * short-lived synthetic signed tokens are saved under ignored target/.
 */
class IdentityRs256B59ContractIT {
    static final String KID = "b59-ephemeral-a";
    static final String ISSUER = "aetheris-identity";
    static final String AUDIENCE = "aetheris-api";

    private static KeyPair generate(int bits) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(bits);
        return generator.generateKeyPair();
    }

    private static IdentityAccount syntheticAccount() {
        IdentityAccount account = new IdentityAccount("B59 Synthetic",
            "b59-admin@example.invalid", "no-real-password-hash", Role.ADMIN);
        ReflectionTestUtils.setField(account, "id", 42L);
        return account;
    }

    private static String issuer(KeyPair key, String kid, String iss, String aud, long expiry) {
        if (expiry < 1 || expiry > JwtService.MAX_ACCESS_TOKEN_LIFETIME_SECONDS) {
            // Negative credential is deliberately created ONLY inside the
            // isolated test signer; production JwtService denies this expiry.
            IdentityAccount account = syntheticAccount();
            Instant now = Instant.now();
            return Jwts.builder().issuer(iss).audience().add(aud).and()
                .subject(account.getEmail()).claim("uid", account.getId())
                .claim("name", account.getName())
                .claim("role", account.getRole().name())
                .claim("scopes", account.getRole().scopes().stream().sorted().toList())
                .issuedAt(Date.from(now)).expiration(Date.from(now.plusSeconds(expiry)))
                .header().keyId(kid).and()
                .signWith(key.getPrivate(), Jwts.SIG.RS256).compact();
        }
        return new JwtService("", expiry, iss, aud, "rs256",
            Base64.getEncoder().encodeToString(key.getPrivate().getEncoded()), kid)
            .issue(syntheticAccount());
    }

    @Test
    void privateKeyOnlyUsedInIdentityAndPublicKeyOnlyExported() throws Exception {
        KeyPair pair = generate(3072);
        KeyPair untrusted = generate(3072);
        String good = issuer(pair, KID, ISSUER, AUDIENCE, 300);
        var parsed = Jwts.parser().verifyWith(pair.getPublic())
            .requireIssuer(ISSUER).requireAudience(AUDIENCE).build().parseSignedClaims(good);
        assertEquals("RS256", parsed.getHeader().getAlgorithm());
        assertEquals(KID, parsed.getHeader().getKeyId());
        assertEquals(42L, ((Number) parsed.getPayload().get("uid")).longValue());

        Path dir = Path.of("target","b59-rsa-contract").toAbsolutePath().normalize();
        assertTrue(dir.startsWith(Path.of("target").toAbsolutePath().normalize()));
        Files.createDirectories(dir);
        String publicB64 = Base64.getEncoder().encodeToString(pair.getPublic().getEncoded());
        // No private PKCS8 or HMAC key is persisted, even in the synthetic CI folder.
        Files.writeString(dir.resolve("public-x509.b64"), publicB64, StandardCharsets.US_ASCII,
            StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING);
        String hmac = new JwtService("test-only-b59-hmac-32bytes-abcdefghijk", 300)
            .issue(syntheticAccount());
        String tampered = good.substring(0, good.lastIndexOf('.') + 1)
            + (good.charAt(good.lastIndexOf('.') + 1) == 'A' ? 'B' : 'A')
            + good.substring(good.lastIndexOf('.') + 2);
        Map<String,String> cases = Map.of(
            "admin", good,
            "unknown-kid", issuer(pair, "unapproved-b59", ISSUER, AUDIENCE, 300),
            "wrong-issuer", issuer(pair, KID, "foreign-issuer", AUDIENCE, 300),
            "wrong-audience", issuer(pair, KID, ISSUER, "aetheris-runtime", 300),
            "wrong-signature", issuer(untrusted, KID, ISSUER, AUDIENCE, 300),
            "expired", issuer(pair, KID, ISSUER, AUDIENCE, -120),
            "legacy-hmac", hmac,
            "tampered", tampered
        );
        for (var entry : cases.entrySet()) {
            String jwt=entry.getValue();
            assertEquals(3, jwt.split("\\.").length);
            Files.writeString(dir.resolve(entry.getKey()+".jwt"),jwt,StandardCharsets.US_ASCII,
                StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING);
        }
        Files.writeString(dir.resolve("test-only.marker"),"B59_EPHEMERAL_IDENTITY_RSA_V1\n",
            StandardCharsets.US_ASCII,StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING);
        assertFalse(Files.exists(dir.resolve("private-key")));
        System.out.println("B59_IDENTITY_ISSUED_RSA_PUBLIC_ONLY_FIXTURES_READY");
    }

    @Test
    void unknownModesMixedKeysWeakKeysAndMissingKidAreDenied() throws Exception {
        KeyPair valid = generate(3072);
        KeyPair weak = generate(2048);
        String der = Base64.getEncoder().encodeToString(valid.getPrivate().getEncoded());
        String weakDer = Base64.getEncoder().encodeToString(weak.getPrivate().getEncoded());
        assertThrows(IllegalArgumentException.class,
            ()->new JwtService("",300,ISSUER,AUDIENCE,"unrecognized",der,KID));
        assertThrows(IllegalArgumentException.class,
            ()->new JwtService("live-legacy-key-must-never-be-used-with-RS256",300,
                ISSUER,AUDIENCE,"rs256",der,KID));
        assertThrows(IllegalArgumentException.class,
            ()->new JwtService("",300,ISSUER,AUDIENCE,"rs256",weakDer,KID));
        assertThrows(IllegalArgumentException.class,
            ()->new JwtService("",300,ISSUER,AUDIENCE,"rs256",der,""));
        assertThrows(IllegalArgumentException.class,
            ()->new JwtService("",300,ISSUER,AUDIENCE,"rs256","not-base64",KID));
        assertThrows(IllegalArgumentException.class,
            ()->new JwtService("test-only-b59-hmac-32bytes-abcdefghijk",300,
                ISSUER,AUDIENCE,"hmac",der,""));
    }
}
