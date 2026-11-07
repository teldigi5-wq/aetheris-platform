package io.aetheris.users.events;

import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class UserEventOriginSignerTest {
    private static KeyPair keys() throws Exception {
        var g = KeyPairGenerator.getInstance("RSA");
        g.initialize(3072);
        return g.generateKeyPair();
    }
    private static String pem(KeyPair pair) {
        return "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getMimeEncoder(64, new byte[]{'\n'})
                    .encodeToString(pair.getPrivate().getEncoded())
                + "\n-----END PRIVATE KEY-----";
    }
    private static Map<String,Object> event() {
        Map<String,Object> e=new LinkedHashMap<>();
        e.put("eventId", UUID.randomUUID().toString());
        e.put("eventType","user.created");
        e.put("occurredAt", Instant.parse("2026-11-06T00:00:00Z").toString());
        e.put("userId", 42L);
        e.put("name", "Synthetic");
        e.put("email","synthetic@example.test");
        e.put("tenantId","aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
        return e;
    }

    @Test
    void optInProducesRealPinnedRs256ProofBindingAllFields() throws Exception {
        var pair=keys();
        var signer=new UserEventOriginSigner(true,pem(pair),"review-producer-1");
        Map<String,Object> original=event();
        Map<String,Object> signed=signer.sign(original);
        assertFalse(original.containsKey("originProof"));
        assertEquals(8,signed.size());
        String proof=(String)signed.get("originProof");
        var jwt=Jwts.parser().verifyWith(pair.getPublic())
                .requireIssuer(UserEventOriginSigner.ISSUER)
                .requireAudience(UserEventOriginSigner.AUDIENCE)
                .build().parseSignedClaims(proof);
        assertEquals("RS256", jwt.getHeader().getAlgorithm());
        assertEquals("review-producer-1",jwt.getHeader().getKeyId());
        assertEquals(original.get("eventId"),jwt.getPayload().getId());
        assertEquals(original.get("eventType"),jwt.getPayload().get("eventType"));
        assertEquals(original.get("name"),jwt.getPayload().get("name"));
        assertEquals(original.get("email"),jwt.getPayload().get("email"));
        assertEquals(original.get("tenantId"),jwt.getPayload().get("tenantId"));
        assertEquals(42, ((Number)jwt.getPayload().get("userId")).longValue());
    }

    @Test
    void directPublisherSignsOnlyAtActualBrokerBoundary() throws Exception {
        var signer=new UserEventOriginSigner(true,pem(keys()),"review-producer-1");
        var rabbit=mock(RabbitTemplate.class);
        var user=new UserEventPublisher(rabbit,null,false,signer);
        user.userCreated(7L,"Synthetic","synthetic@example.test");
        var captor=org.mockito.ArgumentCaptor.forClass(Map.class);
        verify(rabbit).convertAndSend(eq(UserEventPublisher.EXCHANGE),
                eq("user.created"),captor.capture());
        assertTrue(captor.getValue().containsKey("originProof"));
    }

    @Test
    void outboxRelaySignsOnlyAtPublishAndKeepsStableEventId() throws Exception {
        var signer=new UserEventOriginSigner(true,pem(keys()),"review-producer-1");
        UserOutboxStore store=mock(UserOutboxStore.class);
        RabbitTemplate rabbit=mock(RabbitTemplate.class);
        var leased=new UserOutboxStore.LeasedEvent(UUID.randomUUID(),"user.created",
                Instant.parse("2026-11-05T00:00:00Z"),11L,
                "Synthetic","synthetic@example.test",null,1,UUID.randomUUID());
        doAnswer(invocation->{
            CorrelationData confirm=invocation.getArgument(4);
            confirm.getFuture().complete(new CorrelationData.Confirm(true,null));
            return null;
        }).when(rabbit).convertAndSend(anyString(),anyString(),any(),
                any(MessagePostProcessor.class),any(CorrelationData.class));
        new UserOutboxRelay(store,rabbit,8,signer).sendOne(leased);
        var captured=org.mockito.ArgumentCaptor.forClass(Map.class);
        verify(rabbit).convertAndSend(eq(UserEventPublisher.EXCHANGE),eq("user.created"),
                captured.capture(),any(MessagePostProcessor.class),any(CorrelationData.class));
        Map signed=captured.getValue();
        assertEquals(leased.eventId().toString(),signed.get("eventId"));
        assertTrue(signed.containsKey("originProof"));
        assertEquals(7,signed.size());
        verify(store).markDelivered(leased);
        verify(store,never()).markFailure(any());
    }

    @Test
    void rejectUnsafeActivationAndUnsupportedClaims() throws Exception {
        var pair=keys();
        assertThrows(IllegalArgumentException.class,
                ()->new UserEventOriginSigner(true,"","review"));
        assertThrows(IllegalArgumentException.class,
                ()->new UserEventOriginSigner(true,pem(pair),"bad key id"));
        assertThrows(IllegalArgumentException.class,
                ()->new UserEventOriginSigner(false,pem(pair),""));
        var signer=new UserEventOriginSigner(true,pem(pair),"review");
        var forged=event();
        forged.put("eventId","not-a-uuid");
        assertThrows(IllegalArgumentException.class,()->signer.sign(forged));
        var surprise=event();
        surprise.put("untrusted","value");
        assertThrows(IllegalArgumentException.class,()->signer.sign(surprise));
    }
}
