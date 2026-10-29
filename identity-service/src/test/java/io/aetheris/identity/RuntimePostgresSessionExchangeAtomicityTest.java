package io.aetheris.identity;

import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * S0-E6G: real PostgreSQL 16 and real Spring/JPA transaction proxies.
 *
 * Danger guard: only run with the workflow's explicit disposable marker,
 * fixed 127.0.0.1 PostgreSQL URL and uniquely named disposable database.
 * Hibernate create-drop is ONLY for this isolation test; production uses
 * ddl-auto=validate. Never run this test against an owner database.
 *
 * @DataJpaTest normally wraps EACH JUnit method in an outer transaction.
 * That makes data invisible to concurrent workers and can mask rollback.
 * The explicit NOT_SUPPORTED override below is REQUIRED so all service
 * invocations create and commit their own real PostgreSQL transactions.
 */
@DataJpaTest(properties = {
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.open-in-view=false",
    "spring.sql.init.mode=never",
    "spring.main.lazy-initialization=false",
    "spring.jpa.properties.hibernate.jdbc.time_zone=UTC"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
    RefreshTokenService.class,
    RuntimeAudienceSessionExchangeService.class,
    RuntimePostgresSessionExchangeAtomicityTest.SigningFixture.class
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class RuntimePostgresSessionExchangeAtomicityTest {
    private static final String DATABASE_NAME = "aetheris_e6g_disposable";
    private static final String URL =
        "jdbc:postgresql://127.0.0.1:5432/" + DATABASE_NAME;
    private static final KeyPair SYNTHETIC_SIGNER = syntheticKey();

    // @DataJpaTest is a deliberately narrow slice; the issuer exposes a
    // test-clock overload in addition to its public Environment constructor.
    // Explicitly construct the unchanged REAL production issuer from operator
    // fixture properties, without enabling any HTTP entrypoint.
    @TestConfiguration(proxyBeanMethods = false)
    static class SigningFixture {
        @Bean
        RuntimeAudienceTokenIssuer runtimeAudienceTokenIssuer(Environment env) {
            return new RuntimeAudienceTokenIssuer(env);
        }
    }

    @DynamicPropertySource
    static void disposableOnly(DynamicPropertyRegistry properties) {
        if (!"ONLY_DISPOSABLE_E6G_POSTGRES".equals(
                System.getenv("AETHERIS_E6G_DISPOSABLE_MARKER"))
                || !URL.equals(System.getenv("AETHERIS_E6G_JDBC_URL"))) {
            throw new IllegalStateException(
                "E6G PostgreSQL tests require explicit disposable-only marker and localhost DB");
        }
        properties.add("spring.datasource.url", () -> URL);
        properties.add("spring.datasource.username", () -> "e6g_test_only");
        properties.add("spring.datasource.password", () -> "e6g_disposable_not_real_password");
        properties.add("aetheris.runtime-token.pkcs8-base64", () ->
            Base64.getEncoder().encodeToString(
                SYNTHETIC_SIGNER.getPrivate().getEncoded()));
        properties.add("aetheris.runtime-token.key-id", () -> "e6g-postgres-test-key");
        properties.add("aetheris.runtime-token.issuer", () ->
            "https://identity.aetheris.example.invalid");
        properties.add("aetheris.runtime-token.audience", () ->
            "aetheris-orchestrator-api");
    }

    private static KeyPair syntheticKey() {
        try {
            var generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException("Synthetic test key unavailable", e);
        }
    }

    @Autowired IdentityAccountRepository accounts;
    @Autowired RefreshTokenRepository tokens;
    @Autowired RefreshTokenService refresh;
    @Autowired RuntimeAudienceSessionExchangeService exchange;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void cleanOnlyDisposablePostgres() {
        String db = jdbc.queryForObject("select current_database()", String.class);
        assertEquals(DATABASE_NAME, db,
            "Never delete data in a database other than the disposable E6G database");
        String flavor = jdbc.queryForObject("select version()", String.class);
        assertNotNull(flavor);
        assertTrue(flavor.startsWith("PostgreSQL 16."),
            "Must use actual PostgreSQL 16, never H2 or a mock");
        tokens.deleteAllInBatch();
        accounts.deleteAllInBatch();
    }

    private IdentityAccount createAccount(String email, Role role) {
        return accounts.saveAndFlush(new IdentityAccount(
            "Disposable identity", email, "unusable-synthetic-password-hash", role));
    }

    private static String hash(String raw) {
        try {
            return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256")
                    .digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception error) {
            throw new IllegalStateException("Test hash failed", error);
        }
    }

    @Test
    void realPostgresOneTimeRefreshRotatesAndPreservesTheCurrentRole() {
        var user = createAccount("e6g-consumer@example.invalid", Role.API_CONSUMER);
        String original = refresh.issue(user).token();
        assertEquals(1, tokens.count());

        var exchanged = exchange.exchangeUsingSingleUseRefreshCredential(original);
        assertNotNull(exchanged.runtimeAccessToken());
        assertNotEquals(original, exchanged.rotatedRefreshToken().token());
        assertEquals(2, tokens.count(), "One old revoked row and one replacement row");
        assertTrue(tokens.findByTokenHash(hash(original)).orElseThrow().isRevoked());
        assertFalse(tokens.findByTokenHash(hash(exchanged.rotatedRefreshToken().token()))
            .orElseThrow().isRevoked());

        var payload = Jwts.parser().verifyWith(SYNTHETIC_SIGNER.getPublic())
            .build().parseSignedClaims(exchanged.runtimeAccessToken()).getPayload();
        assertEquals(user.getId().longValue(),
            ((Number) payload.get("uid")).longValue());
        assertEquals("API_CONSUMER", payload.get("role", String.class));
        assertEquals(List.of("orchestrator:read"),
            payload.get("scopes", List.class));
        assertFalse(exchanged.toString().contains(exchanged.runtimeAccessToken()));
        assertFalse(exchanged.toString()
            .contains(exchanged.rotatedRefreshToken().token()));
    }

    @Test
    void realPostgresReusedRevokedAndExpiredRefreshCredentialsCannotSign() {
        var user = createAccount("e6g-revoked@example.invalid", Role.DEVELOPER);
        String first = refresh.issue(user).token();
        exchange.exchangeUsingSingleUseRefreshCredential(first);
        assertThrows(RuntimeAudienceSessionExchangeService.ExchangeRejectedException.class,
            () -> exchange.exchangeUsingSingleUseRefreshCredential(first));

        String revoked = refresh.issue(user).token();
        refresh.revoke(revoked);
        assertThrows(RuntimeAudienceSessionExchangeService.ExchangeRejectedException.class,
            () -> exchange.exchangeUsingSingleUseRefreshCredential(revoked));

        String syntheticExpired = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(new byte[32]);
        tokens.saveAndFlush(new RefreshToken(
            user, hash(syntheticExpired), Instant.now().minusSeconds(5)));
        assertThrows(RuntimeAudienceSessionExchangeService.ExchangeRejectedException.class,
            () -> exchange.exchangeUsingSingleUseRefreshCredential(syntheticExpired));
        assertTrue(tokens.findByTokenHash(hash(revoked)).orElseThrow().isRevoked());
    }

    @Test
    void concurrentRealPostgresTransactionsCanClaimOneSessionOnlyOnce()
            throws Exception {
        var user = createAccount("e6g-race@example.invalid", Role.DEVELOPER);
        String first = refresh.issue(user).token();
        int contenders = 6;
        var executor = Executors.newFixedThreadPool(contenders);
        var ready = new CountDownLatch(contenders);
        var start = new CountDownLatch(1);
        var work = new ArrayList<Future<Boolean>>();
        try {
            for (int i = 0; i < contenders; i++) {
                work.add(executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(15, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("E6G worker gate timed out");
                    }
                    try {
                        var result = exchange.exchangeUsingSingleUseRefreshCredential(first);
                        assertNotNull(result.runtimeAccessToken());
                        return true;
                    } catch (RuntimeAudienceSessionExchangeService.ExchangeRejectedException denied) {
                        return false;
                    }
                }));
            }
            assertTrue(ready.await(15, TimeUnit.SECONDS));
            start.countDown();
            int successful = 0;
            for (var future : work) {
                if (future.get(30, TimeUnit.SECONDS)) successful++;
            }
            assertEquals(1, successful,
                "Six real concurrent PostgreSQL transactions must mint only ONE Runtime JWT");
            assertEquals(2, tokens.count(),
                "Only one original token and one replacement may persist");
            assertTrue(tokens.findByTokenHash(hash(first)).orElseThrow().isRevoked());
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(15, TimeUnit.SECONDS));
        }
        System.out.println("AETHERIS_S0_E6G_SIX_REAL_POSTGRES_TRANSACTIONS_ONE_WINNER_PASS");
    }

    @Test
    void issuerFailureRollsBackRealPostgresClaimAndInsertedReplacement() {
        // This account is intentionally malformed only for the disposable DB.
        // The service validates identity presence, then the issuer refuses
        // noncanonical whitespace. The transaction MUST restore the claim.
        var malformed = createAccount(
            " invalid-synthetic@example.invalid ", Role.API_CONSUMER);
        String original = refresh.issue(malformed).token();
        String digest = hash(original);
        assertEquals(1, tokens.count());
        assertThrows(IllegalArgumentException.class,
            () -> exchange.exchangeUsingSingleUseRefreshCredential(original));
        assertEquals(1, tokens.count(),
            "Postgres transaction must roll back the inserted replacement");
        assertFalse(tokens.findByTokenHash(digest).orElseThrow().isRevoked(),
            "Postgres transaction must roll back the revoked flag");

        // Same original token must still work after fixing only test data.
        malformed = accounts.findById(malformed.getId()).orElseThrow();
        org.springframework.test.util.ReflectionTestUtils.setField(
            malformed, "email", "valid-synthetic@example.invalid");
        accounts.saveAndFlush(malformed);
        var recovered = exchange.exchangeUsingSingleUseRefreshCredential(original);
        assertNotNull(recovered.runtimeAccessToken());
        assertEquals(2, tokens.count());
        assertTrue(tokens.findByTokenHash(digest).orElseThrow().isRevoked());
        System.out.println("AETHERIS_S0_E6G_REAL_POSTGRES_ATOMIC_ROLLBACK_PASS");
    }
}
