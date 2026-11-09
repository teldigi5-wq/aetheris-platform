package io.aetheris.audit;

import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.dao.DataAccessResourceFailureException;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AuditEventOriginVerifierTest {
    private static KeyPair key() throws Exception {
        var g=KeyPairGenerator.getInstance("RSA");
        g.initialize(3072);
        return g.generateKeyPair();
    }
    private static String publicPem(KeyPair kp) {
        return "-----BEGIN PUBLIC KEY-----\n"
                + Base64.getMimeEncoder(64,new byte[]{'\n'})
                    .encodeToString(kp.getPublic().getEncoded())
                + "\n-----END PUBLIC KEY-----";
    }
    private static Map<String,Object> message(boolean tenant) {
        Map<String,Object> msg=new LinkedHashMap<>();
        msg.put("eventId",UUID.randomUUID().toString());
        msg.put("eventType","user.created");
        msg.put("occurredAt","2026-11-06T00:00:00Z");
        msg.put("userId",42L);
        msg.put("name","Synthetic Account");
        msg.put("email","synthetic@example.test");
        if(tenant)msg.put("tenantId","aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
        return msg;
    }
    private static String token(KeyPair keys,Map<String,Object> msg,String kid,
                                String issuer, Instant issued,Instant expires) {
        var b=Jwts.builder().issuer(issuer)
                .audience().add("aetheris-audit-domain-event").and()
                .subject("aetheris-user-service").id((String)msg.get("eventId"))
                .claim("eventType",msg.get("eventType"))
                .claim("occurredAt",msg.get("occurredAt"))
                .claim("userId",msg.get("userId"))
                .claim("name",msg.get("name"))
                .claim("email",msg.get("email"))
                .issuedAt(java.util.Date.from(issued))
                .expiration(java.util.Date.from(expires));
        if(msg.containsKey("tenantId"))b.claim("tenantId",msg.get("tenantId"));
        return b.header().keyId(kid).and()
                .signWith(keys.getPrivate(),Jwts.SIG.RS256).compact();
    }
    private static void sign(Map<String,Object> msg,KeyPair kp) {
        Instant now=Instant.now();
        msg.put("originProof",token(kp,msg,"origin-review-key-1",
                "aetheris-user-event-producer",now.minusSeconds(2),now.plusSeconds(180)));
    }
    private static AuditEventStore consumer(KeyPair kp,AuditEventRepository repo) {
        return new AuditEventStore(repo,
                new AuditEventOriginVerifier(true,publicPem(kp),"origin-review-key-1"));
    }
    private static void denied(AuditEventStore store,Map<String,Object> msg) {
        var err=assertThrows(AmqpRejectAndDontRequeueException.class,
                () ->store.consume(msg));
        assertEquals("Rejected malformed User domain event",err.getMessage());
        assertNull(err.getCause());
    }

    @Test
    void verifiedCanonicalAndHistoricalEventsReachRepository() throws Exception {
        KeyPair kp=key();
        var repo=mock(AuditEventRepository.class);
        var store=consumer(kp,repo);
        var scoped=message(true);
        sign(scoped,kp);
        store.consume(scoped);
        var historical=message(false);
        sign(historical,kp);
        store.consume(historical);
        verify(repo,times(2)).saveIfAbsent(any(UserDomainEvent.class));
    }

    @Test
    void forgedMissingStaleAndCrossTenantProofCannotWrite() throws Exception {
        var kp=key();
        var attacker=key();
        var repo=mock(AuditEventRepository.class);
        var store=consumer(kp,repo);
        denied(store,message(true)); // unsigned
        var tampered=message(true);
        sign(tampered,kp);
        tampered.put("name","Forged Account");
        denied(store,tampered);
        var wrongTenant=message(true);
        sign(wrongTenant,kp);
        wrongTenant.put("tenantId","bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb");
        denied(store,wrongTenant);
        var wrongId=message(true);
        sign(wrongId,kp);
        wrongId.put("eventId",UUID.randomUUID().toString());
        denied(store,wrongId);
        var signedByAttacker=message(true);
        sign(signedByAttacker,attacker);
        denied(store,signedByAttacker);
        var wrongKeyId=message(true);
        Instant now=Instant.now();
        wrongKeyId.put("originProof",token(kp,wrongKeyId,"wrong-kid",
                "aetheris-user-event-producer",now.minusSeconds(2),now.plusSeconds(180)));
        denied(store,wrongKeyId);
        var wrongIssuer=message(true);
        wrongIssuer.put("originProof",token(kp,wrongIssuer,"origin-review-key-1",
                "attacker",now.minusSeconds(2),now.plusSeconds(180)));
        denied(store,wrongIssuer);
        var expired=message(true);
        expired.put("originProof",token(kp,expired,"origin-review-key-1",
                "aetheris-user-event-producer",now.minusSeconds(900),now.minusSeconds(700)));
        denied(store,expired);
        var malformed=message(true);
        malformed.put("originProof","not.a.jwt");
        denied(store,malformed);
        verifyNoInteractions(repo);
    }

    @Test
    void failureToReadDatabaseRemainsRetryableAfterSignatureAccepted() throws Exception {
        var kp=key();
        var repo=mock(AuditEventRepository.class);
        doThrow(new DataAccessResourceFailureException("synthetic temporary JDBC failure"))
                .when(repo).saveIfAbsent(any(UserDomainEvent.class));
        var store=consumer(kp,repo);
        var valid=message(true);
        sign(valid,kp);
        assertThrows(DataAccessResourceFailureException.class,
                ()->store.consume(valid));
    }

    @Test
    void optInFailsStartupClosedAndLegacyCannotConfuseSignedEvents() throws Exception {
        var kp=key();
        assertThrows(IllegalArgumentException.class,
                ()->new AuditEventOriginVerifier(true,"","origin-review-key-1"));
        assertThrows(IllegalArgumentException.class,
                ()->new AuditEventOriginVerifier(true,publicPem(kp),"bad key id"));
        assertThrows(IllegalArgumentException.class,
                ()->new AuditEventOriginVerifier(false,publicPem(kp),""));
        var repo=mock(AuditEventRepository.class);
        var legacy=new AuditEventStore(repo,AuditEventOriginVerifier.disabled());
        legacy.consume(message(false)); // retained current legacy contract
        var signed=message(true);
        sign(signed,kp);
        denied(legacy,signed); // non-authoritative proof not silently accepted
        verify(repo,times(1)).saveIfAbsent(any(UserDomainEvent.class));
    }
}
