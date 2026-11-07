package io.aetheris.audit;

import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** Real isolated PostgreSQL16 membership gate and same-transaction race proof. */
class AuditLocalMembershipFenceIT {
    private static String required(String name) {
        String result = System.getenv(name);
        if (result == null || result.isBlank()) throw new IllegalStateException("Missing CI fixture");
        return result;
    }
    private static PGSimpleDataSource source() {
        String run=required("GITHUB_RUN_ID"),attempt=required("GITHUB_RUN_ATTEMPT");
        if (!"true".equals(required("GITHUB_ACTIONS"))
                || !run.matches("[0-9]+") || !attempt.matches("[0-9]+")
                || !required("XERJ_COMPOSE_PROJECT").equals("xerj-mtls-ci-"+run+"-"+attempt)
                || !required("RUNNER_TEMP").startsWith("/home/runner/")) {
            throw new IllegalStateException("Only hosted disposable audit database is permitted");
        }
        var ds=new PGSimpleDataSource();
        ds.setURL("jdbc:postgresql://127.0.0.1:55432/xerj_fence_audit"
                +"?connectTimeout=2&socketTimeout=4");
        ds.setUser("xerj_ci");
        ds.setPassword(required("XERJ_TEST_PG_PASSWORD"));
        return ds;
    }
    private static KeyPair rsa() throws Exception {
        var gen=KeyPairGenerator.getInstance("RSA");
        gen.initialize(3072);
        return gen.generateKeyPair();
    }
    private static AuditEventOriginVerifier verifier(KeyPair keys) {
        String pem="-----BEGIN PUBLIC KEY-----\n"
                + Base64.getMimeEncoder(64,new byte[]{'\n'})
                    .encodeToString(keys.getPublic().getEncoded())
                + "\n-----END PUBLIC KEY-----";
        return new AuditEventOriginVerifier(true,pem,"local-fence-test-key");
    }
    private static Map<String,Object> envelope(KeyPair keys,UUID tenant,long user,Instant occurred) {
        Map<String,Object> msg=new LinkedHashMap<>();
        String id=UUID.randomUUID().toString();
        msg.put("eventId",id);
        msg.put("eventType","user.created");
        msg.put("occurredAt",occurred.toString());
        msg.put("userId",user);
        msg.put("name","Synthetic local fence");
        msg.put("email","fence@example.test");
        if(tenant!=null)msg.put("tenantId",tenant.toString());
        Instant now=Instant.now();
        var token=Jwts.builder()
                .issuer("aetheris-user-event-producer")
                .audience().add("aetheris-audit-domain-event").and()
                .subject("aetheris-user-service").id(id)
                .claim("eventType","user.created")
                .claim("occurredAt",occurred.toString())
                .claim("userId",user)
                .claim("name","Synthetic local fence")
                .claim("email","fence@example.test")
                .issuedAt(Date.from(now.minusSeconds(1)))
                .expiration(Date.from(now.plusSeconds(180)));
        if(tenant!=null)token.claim("tenantId",tenant.toString());
        msg.put("originProof",token.header().keyId("local-fence-test-key").and()
                .signWith(keys.getPrivate(),Jwts.SIG.RS256).compact());
        return msg;
    }
    private static void grant(JdbcTemplate db,UUID tenant,long user,
                              String state,Instant since,long revision) {
        db.update("""
                INSERT INTO audit_user_tenant_membership_fence
                    (tenant_id,user_id,status,valid_since,revision)
                VALUES (?,?,?,?,?)
                ON CONFLICT (tenant_id,user_id) DO UPDATE SET
                    status=EXCLUDED.status, valid_since=EXCLUDED.valid_since,
                    revision=EXCLUDED.revision
                """,tenant,user,state,java.sql.Timestamp.from(since),revision);
    }
    private static void reject(TransactionTemplate tx,AuditEventStore listener,
                               Map<String,Object> msg) {
        var failure=assertThrows(AmqpRejectAndDontRequeueException.class,
                ()->tx.executeWithoutResult(s->listener.consume(msg)));
        assertEquals("Local Audit membership admission denied",failure.getMessage());
        assertNull(failure.getCause());
    }
    private static boolean isExpectedPostgresLockTimeout(Throwable error) {
        Throwable cause=error;
        while(cause!=null){
            if(cause instanceof java.sql.SQLException sql
                    && "55P03".equals(sql.getSQLState())) {
                return true;
            }
            cause=cause.getCause();
        }
        return false;
    }

    @SafeVarargs
    private static void remove(JdbcTemplate db,UUID tenant,long user,
                               Map<String,Object>... events){
        for(Map<String,Object> event:events) {
            if(event!=null)db.update("DELETE FROM audit_events WHERE event_id=?",event.get("eventId"));
        }
        db.update("DELETE FROM audit_user_tenant_membership_fence WHERE tenant_id=? AND user_id=?",
                tenant,user);
    }

    @Test
    void missingRevokedAndPreReauthorizationSignedEventsFailClosed() throws Exception {
        var ds=source();var db=new JdbcTemplate(ds);
        assertEquals("88888888-8888-4888-8888-888888888888",
                db.queryForObject("SELECT token::text FROM public.xerj_dr_fence",String.class));
        var tx=new TransactionTemplate(new DataSourceTransactionManager(ds));
        var keys=rsa();
        var store=new AuditEventStore(new AuditEventRepository(db),verifier(keys),
                new AuditLocalMembershipFence(true,db));
        UUID tenant=UUID.randomUUID();
        long user=2300041L;
        Instant now=Instant.now().minusSeconds(3);
        var oldEvent=envelope(keys,tenant,user,now.minusSeconds(110));
        var afterRevoked=envelope(keys,tenant,user,now.plusSeconds(2));
        var newlyAuthorized=envelope(keys,tenant,user,now.plusSeconds(2));
        try{
            // No broker-created, guessed or implicit membership can be admitted.
            reject(tx,store,oldEvent);
            grant(db,tenant,user,"ACTIVE",now.minusSeconds(3600),1);
            tx.executeWithoutResult(s->store.consume(oldEvent));
            assertEquals(1,db.queryForObject(
                    "SELECT count(*) FROM audit_events WHERE event_id=?",
                    Integer.class,oldEvent.get("eventId")));
            grant(db,tenant,user,"REVOKED",now.minusSeconds(300),2);
            reject(tx,store,afterRevoked);
            assertEquals(0,db.queryForObject(
                    "SELECT count(*) FROM audit_events WHERE event_id=?",
                    Integer.class,afterRevoked.get("eventId")));
            // Reactivation must not unfreeze an old, already signed event.
            grant(db,tenant,user,"ACTIVE",now.minusSeconds(20),3);
            reject(tx,store,oldEvent);
            tx.executeWithoutResult(s->store.consume(newlyAuthorized));
            assertEquals(1,db.queryForObject(
                    "SELECT count(*) FROM audit_events WHERE event_id=?",
                    Integer.class,newlyAuthorized.get("eventId")));
            reject(tx,store,envelope(keys,tenant,user+1,now.plusSeconds(2)));
            reject(tx,store,envelope(keys,null,user,now.plusSeconds(2)));
            System.out.println(
                    "XERJ_LOCAL_MEMBER_FENCE_MISSING_REVOKED_REAUTHORIZATION_DENY_PASS");
        }finally{
            remove(db,tenant,user,oldEvent,afterRevoked,newlyAuthorized);
        }
    }

    @Test
    void concurrentRevocationCannotCommitBetweenLocalCheckAndAuditWrite()
            throws Exception {
        var ds=source();var db=new JdbcTemplate(ds);
        var tx=new TransactionTemplate(new DataSourceTransactionManager(ds));
        var keys=rsa();
        var listener=new AuditEventStore(new AuditEventRepository(db),verifier(keys),
                new AuditLocalMembershipFence(true,db));
        UUID tenant=UUID.randomUUID();
        long user=2300042L;
        var first=envelope(keys,tenant,user,Instant.now().minusSeconds(2));
        var after=envelope(keys,tenant,user,Instant.now());
        grant(db,tenant,user,"ACTIVE",Instant.now().minusSeconds(3600),1);
        var acquired=new CountDownLatch(1);
        try(var pool=Executors.newVirtualThreadPerTaskExecutor()){
            var revoked=pool.submit(()->{
                if(!acquired.await(6,TimeUnit.SECONDS))return false;
                try{
                    tx.executeWithoutResult(s->{
                        db.execute("SET LOCAL lock_timeout = '350ms'");
                        db.update("""
                            UPDATE audit_user_tenant_membership_fence
                            SET status='REVOKED', revision=2
                            WHERE tenant_id=? AND user_id=?
                            """,tenant,user);
                    });
                    return false;
                }catch(DataAccessException lockTimeout){
                    return isExpectedPostgresLockTimeout(lockTimeout);
                }
            });
            try{
                tx.executeWithoutResult(s->{
                    listener.consume(first); // SELECT FOR SHARE + INSERT same txn
                    acquired.countDown();
                    try {
                        assertTrue(revoked.get(6,TimeUnit.SECONDS),
                                "Membership revocation must wait for commit");
                    }catch(Exception cause){
                        throw new IllegalStateException("Race proof did not complete",cause);
                    }
                });
                db.update("""
                    UPDATE audit_user_tenant_membership_fence
                    SET status='REVOKED', revision=2
                    WHERE tenant_id=? AND user_id=?
                    """,tenant,user);
                reject(tx,listener,after);
                assertEquals(1,db.queryForObject(
                        "SELECT count(*) FROM audit_events WHERE event_id=?",
                        Integer.class,first.get("eventId")));
                assertEquals(0,db.queryForObject(
                        "SELECT count(*) FROM audit_events WHERE event_id=?",
                        Integer.class,after.get("eventId")));
                System.out.println("XERJ_AUDIT_LOCAL_MEMBER_REVOKE_DB_SERIALIZATION_PASS");
            }finally{
                remove(db,tenant,user,first,after);
            }
        }
    }

    @Test
    void cannotEnableLocalPolicyWithoutCryptographicProducerIdentity() {
        var db=new JdbcTemplate(source());
        assertThrows(IllegalArgumentException.class,
                ()->new AuditEventStore(new AuditEventRepository(db),
                        AuditEventOriginVerifier.disabled(),
                        new AuditLocalMembershipFence(true,db)));
        assertThrows(IllegalArgumentException.class,
                ()->new AuditLocalMembershipFence(true,null));
        System.out.println("XERJ_LOCAL_MEMBER_FENCE_FAIL_CLOSED_STARTUP_PASS");
    }
}
