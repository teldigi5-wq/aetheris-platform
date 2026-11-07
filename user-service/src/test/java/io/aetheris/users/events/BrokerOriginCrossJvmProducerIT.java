package io.aetheris.users.events;

import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Producer half of cross-Maven/JVM test. Actual UserEventPublisher and
 * UserEventOriginSigner produce JSON onto actual RabbitMQ4; the Audit service
 * test JVM subsequently verifies and persists it in real PostgreSQL16.
 * No production service, key or broker is touched.
 */
class BrokerOriginCrossJvmProducerIT {
    static String required(String key) {
        String value=System.getenv(key);
        if(value==null || value.isBlank()) throw new IllegalStateException("Missing isolated fixture: "+key);
        return value;
    }
    static void preflight() {
        String id=required("GITHUB_RUN_ID"), attempt=required("GITHUB_RUN_ATTEMPT");
        assertEquals("true",required("GITHUB_ACTIONS"));
        assertTrue(id.matches("[0-9]+") && attempt.matches("[0-9]+"));
        assertEquals("xerj-mtls-ci-"+id+"-"+attempt,required("XERJ_COMPOSE_PROJECT"));
        assertTrue(required("RUNNER_TEMP").startsWith("/home/runner/"));
    }
    static String suffix() {
        return required("GITHUB_RUN_ID")+"."+required("GITHUB_RUN_ATTEMPT");
    }
    static ConnectionFactory admin() {
        ConnectionFactory f=new ConnectionFactory();
        f.setHost("127.0.0.1");
        f.setPort(55672);
        f.setVirtualHost("xerj_review");
        f.setUsername("xerj_ci");
        f.setPassword(required("XERJ_TEST_RABBIT_PASSWORD"));
        f.setConnectionTimeout(5000);
        return f;
    }
    static String pem(KeyPair pair,boolean privateMaterial) {
        byte[] material=privateMaterial ? pair.getPrivate().getEncoded():pair.getPublic().getEncoded();
        String type=privateMaterial?"PRIVATE":"PUBLIC";
        return "-----BEGIN "+type+" KEY-----\n"
                +Base64.getMimeEncoder(64,new byte[]{'\n'}).encodeToString(material)
                +"\n-----END "+type+" KEY-----";
    }
    static Map<String,Object> event(String name,UUID tenant) {
        Map<String,Object> m=new LinkedHashMap<>();
        m.put("eventId", UUID.randomUUID().toString());
        m.put("eventType","user.created");
        m.put("occurredAt", Instant.now().toString());
        m.put("userId", 121L);
        m.put("name",name);
        m.put("email","synthetic-integration@example.test");
        if(tenant!=null)m.put("tenantId",tenant.toString());
        return m;
    }
    @Test
    void realUserPublisherAndSignedOutboxShapePublishToDisposableRabbit() throws Exception {
        preflight();
        var generator=KeyPairGenerator.getInstance("RSA");
        generator.initialize(3072);
        KeyPair own=generator.generateKeyPair();
        KeyPair hostile=generator.generateKeyPair();
        String kid="xerj-origin-ci-1";
        var signer=new UserEventOriginSigner(true,pem(own,true),kid);
        var hostileSigner=new UserEventOriginSigner(true,pem(hostile,true),kid);
        Path publicPath=Path.of(required("RUNNER_TEMP"),"xerj-origin-review-public.pem");
        Files.writeString(publicPath,pem(own,false),StandardCharsets.US_ASCII);
        Files.setPosixFilePermissions(publicPath,PosixFilePermissions.fromString("rw-------"));
        String unique=suffix();
        String exchange="aetheris.events";
        String source="xerj.origin.crossjvm."+unique;
        String quarantine="xerj.origin.dead."+unique;
        String dlx="xerj.origin.dlx."+unique;
        // The exchange is the real production publisher contract but exists
        // only in the disposable review vhost. All test queues/exchange are
        // uniquely named; no shared production topology is deleted.
        try(Connection connection=admin().newConnection();
            var channel=connection.createChannel()){
            channel.exchangeDeclare(exchange,"topic",true);
            channel.exchangeDeclare(dlx,"direct",true);
            channel.queueDeclare(quarantine,true,false,false,Map.of("x-expires",300000L));
            channel.queueBind(quarantine,dlx,"invalid");
            channel.queueDeclare(source,true,false,false,Map.of(
                    "x-expires",300000L,"x-dead-letter-exchange",dlx,
                    "x-dead-letter-routing-key","invalid"));
            channel.queueBind(source,exchange,"user.*");
        }
        CachingConnectionFactory cf=new CachingConnectionFactory("127.0.0.1",55672);
        // Actual broker publication uses the least-privilege sender login;
        // the CI-only administrator above solely provisions the test queue.
        cf.setUsername("xerj_publisher");
        cf.setPassword(required("XERJ_BROKER_PUBLISH_PASSWORD"));
        cf.setVirtualHost("xerj_review");
        try{
            RabbitTemplate rabbit=new RabbitTemplate(cf);
            rabbit.setMessageConverter(new Jackson2JsonMessageConverter());
            UUID tenant=UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
            // Exercise real direct UserEventPublisher -> signer -> RabbitTemplate.
            new UserEventPublisher(rabbit,null,false,signer)
                    .userCreated(121L,"signed-direct","synthetic-integration@example.test",tenant);
            // A valid signed event with duplicate replay of the SAME event ID.
            Map<String,Object> stable=signer.sign(event("signed-duplicate",tenant));
            rabbit.convertAndSend(exchange,"user.created",stable);
            rabbit.convertAndSend(exchange,"user.created",stable);
            // Retain legitimate pre-tenant global event compatibility, but signed.
            rabbit.convertAndSend(exchange,"user.created",
                    signer.sign(event("signed-legacy",null)));
            // All three invalid events must reach the real Audit verifier and
            // be negatively acknowledged without creating database effects.
            new UserEventPublisher(rabbit)
                    .userCreated(121L,"unsigned","synthetic-integration@example.test",tenant);
            Map<String,Object> forged=signer.sign(event("forged-tenant",tenant));
            forged.put("tenantId","bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb");
            rabbit.convertAndSend(exchange,"user.created",forged);
            rabbit.convertAndSend(exchange,"user.created",
                    hostileSigner.sign(event("wrong-signing-key",tenant)));
            try(Connection connection=admin().newConnection();
                var channel=connection.createChannel()){
                int found=0;
                for(int i=0;i<50;i++){
                    found=channel.queueDeclarePassive(source).getMessageCount();
                    if(found==7)break;
                    Thread.sleep(100);
                }
                assertEquals(7,found,"All seven actual User-origin messages must be broker-visible");
            }
        }finally{
            cf.destroy();
        }
        System.out.println("XERJ_ACTUAL_USER_SIGNER_TO_RABBITMQ_CROSSJVM_PUBLISH_PASS messages=7");
    }
}
