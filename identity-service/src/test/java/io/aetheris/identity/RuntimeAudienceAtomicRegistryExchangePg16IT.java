package io.aetheris.identity;

import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
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
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Real separate PostgreSQL16 JDBC verifier and actual Spring/JPA transaction
 * atomicity. Explicit -Dtest only, never production; SQL installed using psql
 * in disposable CI before starting this class. No HTTP token exchange route.
 */
@DataJpaTest(properties={
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.open-in-view=false",
    "spring.sql.init.mode=never",
    "spring.main.lazy-initialization=false",
    "spring.jpa.properties.hibernate.jdbc.time_zone=UTC"
})
@AutoConfigureTestDatabase(replace=AutoConfigureTestDatabase.Replace.NONE)
@Import({
    RefreshTokenService.class,
    RuntimeAudienceSessionExchangeService.class,
    RuntimeAudienceAtomicRegistryExchangePg16IT.Fixture.class
})
@Transactional(propagation=Propagation.NOT_SUPPORTED)
class RuntimeAudienceAtomicRegistryExchangePg16IT {
    private static final String DATABASE="aetheris_e6j_disposable";
    private static final String URL="jdbc:postgresql://127.0.0.1:5432/"+DATABASE;
    private static final String ISSUER="https://identity.aetheris.example.invalid";
    private static final String AUDIENCE="aetheris-orchestrator-api";
    private static final String KID="e6k-atomic-review";
    private static final KeyPair KEY=generateSigner();

    @TestConfiguration(proxyBeanMethods=false)
    static class Fixture {
        @Bean RuntimeAudienceTokenIssuer runtimeAudienceTokenIssuer(Environment env) {
            return new RuntimeAudienceTokenIssuer(env);
        }
        @Bean RuntimeAudienceAtomicRegistryExchange registeredReviewExchange(
                RuntimeAudienceSessionExchangeService exchange,
                RuntimeAudienceTokenIssuer issuer,JdbcTemplate jdbc) {
            return new RuntimeAudienceAtomicRegistryExchange(
                exchange,issuer,new RuntimePg16SessionEnrollment(jdbc));
        }
    }

    @DynamicPropertySource
    static void disposableOnly(DynamicPropertyRegistry properties) {
        if (!"ONLY_DISPOSABLE_E6J_POSTGRES".equals(
                System.getenv("AETHERIS_E6J_DISPOSABLE_MARKER"))
                || !"true".equals(System.getenv("GITHUB_ACTIONS"))
                || !"127.0.0.1".equals(System.getenv("PGHOST"))
                || !DATABASE.equals(System.getenv("PGDATABASE"))
                || !"e6j_test_only".equals(System.getenv("PGUSER"))
                || System.getenv("PGPASSWORD")==null) {
            throw new IllegalStateException("S0-E6K must use only disposable PG16");
        }
        properties.add("spring.datasource.url",()->URL);
        properties.add("spring.datasource.username",()->"e6j_test_only");
        properties.add("spring.datasource.password",()->
            "e6j_disposable_ci_only");
        properties.add("aetheris.runtime-token.pkcs8-base64",()->
            Base64.getEncoder().encodeToString(KEY.getPrivate().getEncoded()));
        properties.add("aetheris.runtime-token.key-id",()->KID);
        properties.add("aetheris.runtime-token.issuer",()->ISSUER);
        properties.add("aetheris.runtime-token.audience",()->AUDIENCE);
    }

    private static KeyPair generateSigner() {
        try {
            var generator=KeyPairGenerator.getInstance("RSA");
            generator.initialize(3072);
            return generator.generateKeyPair();
        } catch(Exception e) {
            throw new IllegalStateException("Disposable signer generation failed");
        }
    }

    @Autowired IdentityAccountRepository accounts;
    @Autowired RefreshTokenRepository tokens;
    @Autowired RefreshTokenService refresh;
    @Autowired RuntimeAudienceTokenIssuer issuer;
    @Autowired RuntimeAudienceAtomicRegistryExchange registered;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void resetDisposableOnly() {
        assertEquals(DATABASE,jdbc.queryForObject("SELECT current_database()",
            String.class));
        assertTrue(jdbc.queryForObject("SELECT version()",String.class)
            .startsWith("PostgreSQL 16."));
        assertEquals(1,jdbc.queryForObject("""
            SELECT count(*) FROM pg_namespace
             WHERE nspname='e6j_runtime_auth'""",Integer.class));
        tokens.deleteAllInBatch();
        accounts.deleteAllInBatch();
        jdbc.update("DELETE FROM e6j_runtime_auth.authority_audit");
        jdbc.update("DELETE FROM e6j_runtime_auth.session_registrations");
        jdbc.update("DELETE FROM e6j_runtime_auth.signer_key_states");
        jdbc.update("DELETE FROM e6j_runtime_auth.principal_epochs");
        jdbc.update("""
            INSERT INTO e6j_runtime_auth.signer_key_states(issuer,kid,state)
            VALUES (?,?,'ACTIVE')""",ISSUER,KID);
    }

    @AfterAll
    static void teardownDisposableSchema() throws Exception {
        if(!"ONLY_DISPOSABLE_E6J_POSTGRES".equals(
                System.getenv("AETHERIS_E6J_DISPOSABLE_MARKER")))return;
        try(Connection c=admin();var s=c.createStatement()){
            s.execute("DROP SCHEMA IF EXISTS e6j_runtime_auth CASCADE");
            for(var role:List.of("e6j_reader","e6j_issuer","e6j_revoker",
                    "e6j_outsider","e6j_exec_owner")) {
                s.execute("DROP ROLE IF EXISTS "+role);
            }
            try(var rows=s.executeQuery("""
                SELECT count(*) FROM pg_namespace
                 WHERE nspname='e6j_runtime_auth'""")){
                assertTrue(rows.next());assertEquals(0,rows.getInt(1));
            }
        }
    }

    private static Connection admin() throws Exception {
        return DriverManager.getConnection(URL,"e6j_test_only",
            System.getenv("PGPASSWORD"));
    }

    private IdentityAccount account(String email,Role role) {
        var user=accounts.saveAndFlush(
            new IdentityAccount("Review only",email,"synthetic-unusable-hash",role));
        assertNotNull(user.getId());
        return user;
    }
    private void epoch(IdentityAccount account){
        jdbc.update("""
            INSERT INTO e6j_runtime_auth.principal_epochs(
                principal_id,reject_issued_at_or_before_epoch) VALUES (?,0)
            """,account.getId());
    }
    private static String hash(String token){
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch(Exception ex){throw new IllegalStateException("Hash unavailable");}
    }
    private RuntimeAudienceTokenIssuer.VerifiedRegistration signed(String jwt) {
        return issuer.verifyOwnSignedTokenForEnrollment(jwt);
    }

    // Independent real JDBC caller adopts ONLY the verifier role. It has no
    // table access, and registration must already have committed.
    private static boolean runtimeReader(
            RuntimeAudienceTokenIssuer.VerifiedRegistration facts) throws Exception {
        try(Connection c=admin()) {
            c.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            c.setAutoCommit(false);
            try(var set=c.createStatement()){set.execute("SET LOCAL ROLE e6j_reader");}
            try(PreparedStatement p=c.prepareStatement("""
                    SELECT e6j_runtime_auth.check_signed_session(?,?,?,?,?)
                    """)){
                p.setString(1,facts.issuer());p.setString(2,facts.kid());
                p.setString(3,facts.jti());p.setLong(4,facts.uid());
                p.setLong(5,facts.issuedAtSeconds());
                try(var rows=p.executeQuery()){
                    assertTrue(rows.next());boolean active=rows.getBoolean(1);
                    assertFalse(rows.next());c.commit();return active;
                }
            }
        }
    }

    @Test
    void actualSpringProxyAndTransactionBoundaryAreMandatory() {
        assertTrue(org.springframework.aop.support.AopUtils.isAopProxy(registered));
        assertTrue(org.springframework.aop.support.AopUtils.isCglibProxy(registered));
        assertTrue(org.springframework.aop.support.AopUtils.isAopProxy(
            org.springframework.test.util.ReflectionTestUtils.getField(
                registered,"exchange")) ||
            org.springframework.aop.support.AopUtils.isAopProxy(
                org.springframework.test.util.ReflectionTestUtils.getField(
                    org.springframework.aop.framework.AopProxyUtils.getSingletonTarget(registered),
                    "exchange")));
    }

    @Test
    void independentlySignedTwoUsersHaveDistinctCommittedRegisteredJti()
            throws Exception {
        var a=account("e6k-a@example.invalid",Role.DEVELOPER);
        var b=account("e6k-b@example.invalid",Role.API_CONSUMER);
        epoch(a);epoch(b);
        var unregistered=issuer.issueForVerifiedExchange(a);
        assertFalse(runtimeReader(signed(unregistered)),
            "An RS256 signature is insufficient without ledger registration");
        var first=registered.exchangeAndEnrollInternal(refresh.issue(a).token());
        var second=registered.exchangeAndEnrollInternal(refresh.issue(b).token());
        var af=signed(first.runtimeAccessToken());
        var bf=signed(second.runtimeAccessToken());
        assertEquals(a.getId().longValue(),af.uid());
        assertEquals(b.getId().longValue(),bf.uid());
        assertNotEquals(af.jti(),bf.jti());
        assertTrue(runtimeReader(af));
        assertTrue(runtimeReader(bf));
        assertEquals(2,jdbc.queryForObject(
            "SELECT count(*) FROM e6j_runtime_auth.session_registrations",Integer.class));
        assertEquals(2,jdbc.queryForObject(
            "SELECT count(*) FROM e6j_runtime_auth.authority_audit",Integer.class));
        assertFalse(first.toString().contains(first.runtimeAccessToken()));
        assertFalse(first.toString().contains(first.rotatedRefreshToken().token()));
        var parsed=Jwts.parser().verifyWith(KEY.getPublic()).build()
            .parseSignedClaims(second.runtimeAccessToken()).getPayload();
        assertEquals("API_CONSUMER",parsed.get("role",String.class));
        assertEquals(List.of("orchestrator:read"),parsed.get("scopes",List.class));
    }

    @Test
    void absentEpochRollsBackRefreshClaimAndAllowsRetryWhenAuthorityExists()
            throws Exception {
        var a=account("e6k-epoch@example.invalid",Role.DEVELOPER);
        String original=refresh.issue(a).token();
        assertEquals(1,tokens.count());
        assertThrows(RuntimePg16SessionEnrollment.SessionEnrollmentRejectedException.class,
            ()->registered.exchangeAndEnrollInternal(original));
        assertEquals(1,tokens.count());
        assertFalse(tokens.findByTokenHash(hash(original)).orElseThrow().isRevoked());
        assertEquals(0,jdbc.queryForObject(
            "SELECT count(*) FROM e6j_runtime_auth.session_registrations",Integer.class));
        assertEquals(0,jdbc.queryForObject(
            "SELECT count(*) FROM e6j_runtime_auth.authority_audit",Integer.class));
        epoch(a);
        var result=registered.exchangeAndEnrollInternal(original);
        assertTrue(runtimeReader(signed(result.runtimeAccessToken())));
        assertEquals(2,tokens.count());
    }

    @Test
    void revokedSignerRollsBackRegistrationAndRefreshRotation()
            throws Exception {
        var a=account("e6k-blocked@example.invalid",Role.ADMIN);
        epoch(a);String original=refresh.issue(a).token();
        jdbc.update("UPDATE e6j_runtime_auth.signer_key_states SET state='REVOKED'");
        assertThrows(RuntimePg16SessionEnrollment.SessionEnrollmentRejectedException.class,
            ()->registered.exchangeAndEnrollInternal(original));
        assertEquals(1,tokens.count());
        assertFalse(tokens.findByTokenHash(hash(original)).orElseThrow().isRevoked());
        assertEquals(0,jdbc.queryForObject(
            "SELECT count(*) FROM e6j_runtime_auth.session_registrations",Integer.class));
        jdbc.update("UPDATE e6j_runtime_auth.signer_key_states SET state='ACTIVE'");
        var recovered=registered.exchangeAndEnrollInternal(original);
        assertTrue(runtimeReader(signed(recovered.runtimeAccessToken())));
    }

    @Test
    void roleBasedReaderSeesRevocationButCannotChangeAuthority()
            throws Exception {
        var a=account("e6k-revoke@example.invalid",Role.API_CONSUMER);
        epoch(a);
        var result=registered.exchangeAndEnrollInternal(refresh.issue(a).token());
        var facts=signed(result.runtimeAccessToken());
        assertTrue(runtimeReader(facts));
        try(Connection c=admin()){
            c.setAutoCommit(false);
            try(var s=c.createStatement()){s.execute("SET LOCAL ROLE e6j_revoker");}
            try(var p=c.prepareStatement("""
                    SELECT e6j_runtime_auth.revoke_signed_session(?,?,?,?)
                    """)){
                p.setString(1,facts.issuer());p.setString(2,facts.kid());
                p.setString(3,facts.jti());p.setObject(4,UUID.randomUUID());
                try(var rows=p.executeQuery()){
                    assertTrue(rows.next());assertTrue(rows.getBoolean(1));
                }
            }
            c.commit();
        }
        assertFalse(runtimeReader(facts));
        // Revocation changes authorization without invalidating RS256 math.
        assertNotNull(Jwts.parser().verifyWith(KEY.getPublic()).build()
            .parseSignedClaims(result.runtimeAccessToken()));
    }

    @Test
    void signedIssuerRejectsTamperedOrForeignTokenBeforeDatabaseAdmission() {
        var a=account("e6k-claims@example.invalid",Role.DEVELOPER);
        var token=issuer.issueForVerifiedExchange(a);
        assertNotNull(signed(token));
        var segments=token.split("\\.");
        var payload=segments[1];
        char last=payload.charAt(payload.length()-1);
        segments[1]=payload.substring(0,payload.length()-1)
            +(last=='A'?'B':'A');
        assertThrows(IllegalStateException.class,
            ()->signed(String.join(".",segments)));
        assertThrows(IllegalStateException.class,
            ()->signed("not-a-jwt"));
    }

    @Test
    void sixIndependentRefreshClaimsCommitOnlyOneRegisteredSession()
            throws Exception {
        var a=account("e6k-race@example.invalid",Role.DEVELOPER);
        epoch(a);
        String original=refresh.issue(a).token();
        int workers=6;
        var pool=Executors.newFixedThreadPool(workers);
        var start=new CountDownLatch(1);
        var ready=new CountDownLatch(workers);
        var results=new ArrayList<Future<String>>();
        try {
            for(int i=0;i<workers;i++){
                results.add(pool.submit(()->{
                    ready.countDown();
                    if(!start.await(15,TimeUnit.SECONDS))
                        throw new IllegalStateException("Review race timeout");
                    try{
                        return registered.exchangeAndEnrollInternal(original)
                            .runtimeAccessToken();
                    }catch(RuntimeAudienceSessionExchangeService.ExchangeRejectedException denied){
                        return null;
                    }
                }));
            }
            assertTrue(ready.await(15,TimeUnit.SECONDS));
            start.countDown();
            var committed=new ArrayList<String>();
            for(var future:results){
                var jwt=future.get(30,TimeUnit.SECONDS);
                if(jwt!=null) committed.add(jwt);
            }
            assertEquals(1,committed.size());
            assertTrue(runtimeReader(signed(committed.get(0))));
            assertEquals(2,tokens.count());
            assertTrue(tokens.findByTokenHash(hash(original)).orElseThrow().isRevoked());
            assertEquals(1,jdbc.queryForObject(
                "SELECT count(*) FROM e6j_runtime_auth.session_registrations",Integer.class));
            assertEquals(1,jdbc.queryForObject(
                "SELECT count(*) FROM e6j_runtime_auth.authority_audit",Integer.class));
        }finally{
            start.countDown();pool.shutdownNow();
            assertTrue(pool.awaitTermination(15,TimeUnit.SECONDS));
        }
    }
}
