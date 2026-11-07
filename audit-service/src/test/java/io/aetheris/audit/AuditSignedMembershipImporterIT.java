package io.aetheris.audit;

import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AuditSignedMembershipImporterIT {
    private static String required(String name) {
        String value=System.getenv(name);
        if(value==null||value.isBlank())throw new IllegalStateException("Missing disposable fixture");
        return value;
    }
    private static JdbcTemplate db() {
        String run=required("GITHUB_RUN_ID");
        String attempt=required("GITHUB_RUN_ATTEMPT");
        if(!"true".equals(required("GITHUB_ACTIONS"))||!run.matches("[0-9]+")
                ||!attempt.matches("[0-9]+")
                ||!required("XERJ_COMPOSE_PROJECT").equals("xerj-mtls-ci-"+run+"-"+attempt)
                ||!required("RUNNER_TEMP").startsWith("/home/runner/")) {
            throw new IllegalStateException("Not the expected isolated CI PostgreSQL");
        }
        var ds=new PGSimpleDataSource();
        ds.setURL("jdbc:postgresql://127.0.0.1:55432/xerj_fence_audit"
                +"?connectTimeout=3&socketTimeout=5");
        ds.setUser("xerj_ci");
        ds.setPassword(required("XERJ_TEST_PG_PASSWORD"));
        return new JdbcTemplate(ds);
    }
    private static KeyPair keys() throws Exception {
        var generator=KeyPairGenerator.getInstance("RSA");
        generator.initialize(3072);
        return generator.generateKeyPair();
    }
    private static String publicPem(KeyPair pair) {
        return "-----BEGIN PUBLIC KEY-----\n"
                +Base64.getMimeEncoder(64,new byte[]{'\n'})
                    .encodeToString(pair.getPublic().getEncoded())
                +"\n-----END PUBLIC KEY-----";
    }
    private static AuditSignedMembershipImporter importer(KeyPair pair,JdbcTemplate jdbc) {
        return new AuditSignedMembershipImporter(true,publicPem(pair),
                "test-independent-authority","test-user-event-origin",jdbc);
    }
    private static String sign(KeyPair pair,String issuer,String audience,String kid,
                               UUID tenant,long user,String status,long revision,
                               Instant since,Instant issued,Instant exp,
                               boolean extraClaim) {
        var jwt=Jwts.builder().issuer(issuer).audience().add(audience).and()
                .subject(AuditSignedMembershipImporter.SUBJECT)
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(issued))
                .expiration(Date.from(exp))
                .claim("tenantId",tenant.toString())
                .claim("userId",user)
                .claim("status",status)
                .claim("revision",revision)
                .claim("validSince",since.toString());
        if(extraClaim)jwt.claim("unreviewed",true);
        return jwt.header().keyId(kid).and()
                .signWith(pair.getPrivate(),Jwts.SIG.RS256).compact();
    }
    private static String good(KeyPair pair,UUID tenant,long user,
                               String status,long revision,Instant since) {
        Instant now=Instant.now();
        return sign(pair,AuditSignedMembershipImporter.ISSUER,
                AuditSignedMembershipImporter.AUDIENCE,"test-independent-authority",
                tenant,user,status,revision,since,
                now.minusSeconds(1),now.plusSeconds(180),false);
    }
    private static String status(JdbcTemplate db,UUID tenant,long user) {
        return db.queryForObject("""
                SELECT status FROM audit_user_tenant_membership_fence
                WHERE tenant_id=? AND user_id=?
                """,String.class,tenant,user);
    }
    private static long revision(JdbcTemplate db,UUID tenant,long user) {
        return db.queryForObject("""
                SELECT revision FROM audit_user_tenant_membership_fence
                WHERE tenant_id=? AND user_id=?
                """,Long.class,tenant,user);
    }
    private static void cleanup(JdbcTemplate db,UUID tenant,long user) {
        db.update("DELETE FROM audit_user_tenant_membership_fence WHERE tenant_id=? AND user_id=?",
                tenant,user);
    }
    private static void denied(AuditSignedMembershipImporter importer,String token) {
        var exception=assertThrows(IllegalArgumentException.class,
                ()->importer.importSigned(token));
        assertTrue(exception.getMessage().equals("Invalid membership authority proof")
                ||exception.getMessage().equals(
                        "Stale or conflicting membership authority revision"));
        assertNull(exception.getCause());
    }

    @Test
    void separateAuthoritySignatureRevisionsAndRevocationAreRealPostgresCAS()
            throws Exception {
        var jdbc=db();
        assertEquals("88888888-8888-4888-8888-888888888888",
                jdbc.queryForObject("SELECT token::text FROM public.xerj_dr_fence",
                        String.class));
        var signer=keys();
        var source=importer(signer,jdbc);
        UUID tenant=UUID.randomUUID();
        long user=2400011L;
        Instant now=Instant.now();
        Instant initial=now.minusSeconds(3600);
        Instant revoked=now.minusSeconds(120);
        Instant fresh=now.minusSeconds(10);
        try {
            source.importSigned(good(signer,tenant,user,"ACTIVE",1,initial));
            assertEquals(1L,revision(jdbc,tenant,user));
            assertEquals("ACTIVE",status(jdbc,tenant,user));
            denied(source,good(signer,tenant,user,"REVOKED",1,revoked));
            denied(source,good(signer,tenant,user,"REVOKED",0,revoked));
            assertEquals("ACTIVE",status(jdbc,tenant,user));
            source.importSigned(good(signer,tenant,user,"REVOKED",2,revoked));
            assertEquals("REVOKED",status(jdbc,tenant,user));
            denied(source,good(signer,tenant,user,"ACTIVE",3,revoked));
            denied(source,good(signer,tenant,user,"ACTIVE",3,initial));
            assertEquals(2L,revision(jdbc,tenant,user));
            source.importSigned(good(signer,tenant,user,"ACTIVE",3,fresh));
            assertEquals(3L,revision(jdbc,tenant,user));
            assertEquals("ACTIVE",status(jdbc,tenant,user));
            // Existing Audit local fence sees imported state, but it is still
            // not a substitute for a real independently trusted source.
            var fence=new AuditLocalMembershipFence(true,jdbc);
            assertThrows(org.springframework.amqp.AmqpRejectAndDontRequeueException.class,
                    ()->fence.enforce(new UserDomainEvent(
                            UUID.randomUUID().toString(),"user.created",
                            revoked.minusSeconds(1),user,"synthetic",
                            "review@example.test",tenant)));
            fence.enforce(new UserDomainEvent(
                    UUID.randomUUID().toString(),"user.created",
                    now,user,"synthetic","review@example.test",tenant));
            System.out.println(
                    "XERJ_SIGNED_AUTHORITY_MEMBERSHIP_REVOCATION_MONOTONIC_CAS_PASS");
        } finally {
            cleanup(jdbc,tenant,user);
        }
    }

    @Test
    void invalidSignatureIssuerAudienceKeyAndExpiryCannotSeedMembership()
            throws Exception {
        var db=db();
        var signer=keys();
        var attacker=keys();
        var receiver=importer(signer,db);
        UUID tenant=UUID.randomUUID();
        long user=2400012L;
        Instant now=Instant.now();
        try {
            denied(receiver,good(attacker,tenant,user,"ACTIVE",1,now.minusSeconds(30)));
            denied(receiver,sign(signer,"wrong-issuer",
                    AuditSignedMembershipImporter.AUDIENCE,"test-independent-authority",
                    tenant,user,"ACTIVE",1,now.minusSeconds(30),
                    now.minusSeconds(1),now.plusSeconds(120),false));
            denied(receiver,sign(signer,AuditSignedMembershipImporter.ISSUER,
                    "incorrect-audience","test-independent-authority",
                    tenant,user,"ACTIVE",1,now.minusSeconds(30),
                    now.minusSeconds(1),now.plusSeconds(120),false));
            denied(receiver,sign(signer,AuditSignedMembershipImporter.ISSUER,
                    AuditSignedMembershipImporter.AUDIENCE,"wrong-key-id",
                    tenant,user,"ACTIVE",1,now.minusSeconds(30),
                    now.minusSeconds(1),now.plusSeconds(120),false));
            denied(receiver,sign(signer,AuditSignedMembershipImporter.ISSUER,
                    AuditSignedMembershipImporter.AUDIENCE,"test-independent-authority",
                    tenant,user,"ACTIVE",1,now.minusSeconds(30),
                    now.minusSeconds(900),now.minusSeconds(600),false));
            denied(receiver,sign(signer,AuditSignedMembershipImporter.ISSUER,
                    AuditSignedMembershipImporter.AUDIENCE,"test-independent-authority",
                    tenant,user,"ACTIVE",1,now.minusSeconds(30),
                    now.minusSeconds(1),now.plusSeconds(500),false));
            denied(receiver,sign(signer,AuditSignedMembershipImporter.ISSUER,
                    AuditSignedMembershipImporter.AUDIENCE,"test-independent-authority",
                    tenant,user,"ACTIVE",1,now.minusSeconds(30),
                    now.minusSeconds(1),now.plusSeconds(100),true));
            denied(receiver,"not.a.signed.jwt");
            assertEquals(0,db.queryForObject(
                    "SELECT count(*) FROM audit_user_tenant_membership_fence WHERE tenant_id=? AND user_id=?",
                    Integer.class,tenant,user));
            System.out.println("XERJ_UNTRUSTED_MEMBERSHIP_PROOFS_DENIED_NO_DB_EFFECT_PASS");
        } finally {
            cleanup(db,tenant,user);
        }
    }

    @Test
    void concurrentSignedUpdatesCannotRollbackLargerLocalRevision() throws Exception {
        var jdbc=db();
        var pair=keys();
        var receiver=importer(pair,jdbc);
        UUID tenant=UUID.randomUUID();
        long user=2400013L;
        Instant now=Instant.now();
        Instant start=now.minusSeconds(3600);
        receiver.importSigned(good(pair,tenant,user,"ACTIVE",1,start));
        String v2=good(pair,tenant,user,"REVOKED",2,now.minusSeconds(60));
        String v3=good(pair,tenant,user,"REVOKED",3,now.minusSeconds(30));
        var go=new CountDownLatch(1);
        try(var pool=Executors.newVirtualThreadPerTaskExecutor()) {
            var two=pool.submit(()->{
                if(!go.await(10,TimeUnit.SECONDS))throw new AssertionError("Race barrier");
                try{receiver.importSigned(v2);}catch(IllegalArgumentException expected){
                    assertEquals("Stale or conflicting membership authority revision",
                            expected.getMessage());
                }
                return true;
            });
            var three=pool.submit(()->{
                if(!go.await(10,TimeUnit.SECONDS))throw new AssertionError("Race barrier");
                receiver.importSigned(v3);
                return true;
            });
            try {
                go.countDown();
                assertTrue(two.get(12,TimeUnit.SECONDS));
                assertTrue(three.get(12,TimeUnit.SECONDS));
                assertEquals(3L,revision(jdbc,tenant,user));
                assertEquals("REVOKED",status(jdbc,tenant,user));
                denied(receiver,v2);
                System.out.println("XERJ_MEMBERSHIP_REVISION_CONCURRENT_REORDER_DENIED_PASS");
            } finally {
                cleanup(jdbc,tenant,user);
            }
        }
    }

    @Test
    void importerMustHaveDistinctPinnedAuthorityAndPreserveJdbcRetry() throws Exception {
        var signer=keys();
        var pem=publicPem(signer);
        assertThrows(IllegalArgumentException.class,
                ()->new AuditSignedMembershipImporter(true,pem,
                        "same-kid","same-kid",db()));
        assertThrows(IllegalArgumentException.class,
                ()->new AuditSignedMembershipImporter(true,"",
                        "test-independent-authority","other",db()));
        assertThrows(IllegalArgumentException.class,
                ()->new AuditSignedMembershipImporter(false,pem,
                        "test-independent-authority","other",db()));
        var absent=new AuditSignedMembershipImporter(false,"","","other",db());
        assertThrows(IllegalStateException.class,
                ()->absent.importSigned("not-a-jwt"));
        var jdbc=mock(JdbcTemplate.class);
        when(jdbc.query(anyString(),any(org.springframework.jdbc.core.RowMapper.class),
                any(Object[].class))).thenThrow(
                        new DataAccessResourceFailureException("synthetic JDBC outage"));
        var importer=importer(signer,jdbc);
        UUID tenant=UUID.randomUUID();
        assertThrows(DataAccessResourceFailureException.class,
                ()->importer.importSigned(good(signer,tenant,2400014L,
                        "ACTIVE",1,Instant.now().minusSeconds(10))));
        System.out.println("XERJ_SEPARATE_AUTHORITY_FAIL_CLOSED_CONFIG_RETRY_PASS");
    }
}
