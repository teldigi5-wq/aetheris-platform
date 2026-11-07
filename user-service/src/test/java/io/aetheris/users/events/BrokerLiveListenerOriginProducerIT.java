package io.aetheris.users.events;

import com.rabbitmq.client.ConnectionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Disposable real Spring @RabbitListener test, publisher half. A CI-only admin
 * declares topology, but the actual UserEventPublisher sends as publisher-only.
 * The separate signed-mode Audit JAR starts after this test completes.
 */
class BrokerLiveListenerOriginProducerIT {
    static String need(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException("Missing isolated fixture "+name);
        return value;
    }
    static void isolate() {
        String run = need("GITHUB_RUN_ID"), attempt = need("GITHUB_RUN_ATTEMPT");
        assertEquals("true", need("GITHUB_ACTIONS"));
        assertTrue(run.matches("[0-9]+") && attempt.matches("[0-9]+"));
        assertEquals("xerj-mtls-ci-"+run+"-"+attempt, need("XERJ_COMPOSE_PROJECT"));
        assertTrue(need("RUNNER_TEMP").startsWith("/home/runner/"));
        assertEquals("xerj_origin_listener", need("XERJ_ORIGIN_LISTENER_VHOST"));
    }
    static ConnectionFactory admin() {
        var cf = new ConnectionFactory();
        cf.setHost("127.0.0.1");
        cf.setPort(55672);
        cf.setVirtualHost(need("XERJ_ORIGIN_LISTENER_VHOST"));
        cf.setUsername("xerj_ci");
        cf.setPassword(need("XERJ_TEST_RABBIT_PASSWORD"));
        cf.setConnectionTimeout(5000);
        return cf;
    }
    static Map<String,Object> event(String name, UUID tenant) {
        Map<String,Object> event = new LinkedHashMap<>();
        event.put("eventId", UUID.randomUUID().toString());
        event.put("eventType", "user.created");
        event.put("occurredAt", Instant.now().toString());
        event.put("userId", 121L);
        event.put("name",name);
        event.put("email","synthetic-listener@example.test");
        if (tenant != null) event.put("tenantId",tenant.toString());
        return event;
    }
    @Test
    void actualUserPublisherSendsSevenEventsIntoRestrictedListenerQueue() throws Exception {
        isolate();
        Path privatePath=Path.of(need("XERJ_ORIGIN_PRIVATE_PEM_PATH")).toRealPath();
        assertTrue(privatePath.startsWith(Path.of(need("RUNNER_TEMP")).toRealPath()));
        String pem=Files.readString(privatePath, StandardCharsets.US_ASCII);
        String base=Base64.getEncoder().encodeToString(new byte[0]);
        assertEquals("",base);
        String raw=pem.replace("-----BEGIN PRIVATE KEY-----","")
                .replace("-----END PRIVATE KEY-----","").replaceAll("\\s","");
        var key=KeyFactory.getInstance("RSA")
                .generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(raw)));
        assertTrue(key instanceof RSAPrivateKey rsa && rsa.getModulus().bitLength()>=3072);
        var signer=new UserEventOriginSigner(true,pem,"xerj-listener-ci-1");
        String suffix=need("GITHUB_RUN_ID")+"-"+need("GITHUB_RUN_ATTEMPT");
        String prefix="xerj-live-"+suffix+"-";
        String okDirect=prefix+"signed-direct";
        String okDuplicate=prefix+"signed-duplicate";
        String okLegacy=prefix+"signed-legacy";
        String badUnsigned=prefix+"unsigned";
        String badTenant=prefix+"tenant-tampered";
        String badKey=prefix+"wrong-key";
        String queue="aetheris.audit.user-events";
        String dlx="xerj.live.origin.dlx."+suffix;
        String dlq="xerj.live.origin.quarantine."+suffix;
        try(var connection=admin().newConnection();
            var channel=connection.createChannel()) {
            channel.exchangeDeclare("aetheris.events","topic",true);
            channel.exchangeDeclare(dlx,"direct",true);
            channel.queueDeclare(dlq,true,false,false,Map.of("x-expires",300000L));
            channel.queueBind(dlq,dlx,"invalid");
            channel.queueDeclare(queue,true,false,false,Map.of(
                    "x-dead-letter-exchange",dlx,
                    "x-dead-letter-routing-key","invalid"));
            channel.queueBind(queue,"aetheris.events","user.*");
        }
        var publisher=new CachingConnectionFactory("127.0.0.1",55672);
        publisher.setVirtualHost(need("XERJ_ORIGIN_LISTENER_VHOST"));
        publisher.setUsername("xerj_publisher");
        publisher.setPassword(need("XERJ_BROKER_PUBLISH_PASSWORD"));
        try {
            var rabbit=new RabbitTemplate(publisher);
            rabbit.setMessageConverter(new Jackson2JsonMessageConverter());
            UUID tenant=UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
            new UserEventPublisher(rabbit,null,false,signer).userCreated(
                    121L,okDirect,"synthetic-listener@example.test",tenant);
            var duplicate=signer.sign(event(okDuplicate,tenant));
            rabbit.convertAndSend("aetheris.events","user.created",duplicate);
            rabbit.convertAndSend("aetheris.events","user.created",duplicate);
            rabbit.convertAndSend("aetheris.events","user.created",
                    signer.sign(event(okLegacy,null)));
            new UserEventPublisher(rabbit).userCreated(
                    121L,badUnsigned,"synthetic-listener@example.test",tenant);
            var modified=signer.sign(event(badTenant,tenant));
            modified.put("tenantId","bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb");
            rabbit.convertAndSend("aetheris.events","user.created",modified);
            // A valid JWS from a DIFFERENT private key must not be accepted.
            var generator=java.security.KeyPairGenerator.getInstance("RSA");
            generator.initialize(3072);
            var attacker=generator.generateKeyPair();
            String attackerPem="-----BEGIN PRIVATE KEY-----\n"
                    +Base64.getMimeEncoder(64,new byte[]{'\n'})
                        .encodeToString(attacker.getPrivate().getEncoded())
                    +"\n-----END PRIVATE KEY-----";
            rabbit.convertAndSend("aetheris.events","user.created",
                    new UserEventOriginSigner(true,attackerPem,"xerj-listener-ci-1")
                            .sign(event(badKey,tenant)));
        } finally {
            publisher.destroy();
        }
        try(var connection=admin().newConnection();
            var channel=connection.createChannel()) {
            int count=0;
            for(int i=0;i<40;i++){
                count=channel.queueDeclarePassive(queue).getMessageCount();
                if(count==7)break;
                Thread.sleep(100);
            }
            assertEquals(7,count,"Listener must not be started before producer fixture");
        }
        Path names=Path.of(need("XERJ_ORIGIN_NAMES_PATH"));
        assertTrue(names.toAbsolutePath().normalize().startsWith(
                Path.of(need("RUNNER_TEMP")).toAbsolutePath().normalize()));
        Files.write(names,List.of(okDirect,okDuplicate,okLegacy,
                badUnsigned,badTenant,badKey,dlq),StandardCharsets.US_ASCII);
        System.out.println("XERJ_REAL_LISTENER_USER_PUBLISH_ONLY_SIGNED_FIXTURE_PASS messages=7");
    }
}
