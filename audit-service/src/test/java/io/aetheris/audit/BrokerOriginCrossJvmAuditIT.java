package io.aetheris.audit;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.GetResponse;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Consumer half of cross-Maven/JVM proof. Reads actual User-service signed
 * JSON from RabbitMQ4 with manual ACK/NACK, runs actual AuditEventStore and
 * verifier on a separate JVM and checks real PostgreSQL16 durable effects.
 * No Spring AMQP annotation container dispatch or deployed service is claimed.
 */
class BrokerOriginCrossJvmAuditIT {
    private static String required(String key) {
        String value=System.getenv(key);
        if(value==null || value.isBlank())throw new IllegalStateException("Missing isolated fixture: "+key);
        return value;
    }
    private static void preflight() {
        String run=required("GITHUB_RUN_ID");
        String attempt=required("GITHUB_RUN_ATTEMPT");
        assertEquals("true",required("GITHUB_ACTIONS"));
        assertTrue(run.matches("[0-9]+") && attempt.matches("[0-9]+"));
        assertEquals("xerj-mtls-ci-"+run+"-"+attempt,required("XERJ_COMPOSE_PROJECT"));
        assertTrue(required("RUNNER_TEMP").startsWith("/home/runner/"));
    }
    private static ConnectionFactory broker() {
        ConnectionFactory f=new ConnectionFactory();
        f.setHost("127.0.0.1");
        f.setPort(55672);
        f.setVirtualHost("xerj_review");
        f.setUsername("xerj_ci");
        f.setPassword(required("XERJ_TEST_RABBIT_PASSWORD"));
        f.setConnectionTimeout(5000);
        return f;
    }
    private static JdbcTemplate auditDatabase() {
        PGSimpleDataSource data=new PGSimpleDataSource();
        data.setURL("jdbc:postgresql://127.0.0.1:55432/xerj_fence_audit"
                +"?connectTimeout=2&socketTimeout=4");
        data.setUser("xerj_ci");
        data.setPassword(required("XERJ_TEST_PG_PASSWORD"));
        return new JdbcTemplate(data);
    }

    @Test
    void actualSignedUserOriginCrossesRealBrokerIntoDurableAuditWithDlq()
            throws Exception {
        preflight();
        String suffix=required("GITHUB_RUN_ID")+"."+required("GITHUB_RUN_ATTEMPT");
        String source="xerj.origin.crossjvm."+suffix;
        String dlq="xerj.origin.dead."+suffix;
        String dlx="xerj.origin.dlx."+suffix;
        Path pub=Path.of(required("RUNNER_TEMP"),"xerj-origin-review-public.pem");
        assertTrue(Files.isRegularFile(pub),"Only producer test may supply public key");
        String publicPem=Files.readString(pub,StandardCharsets.US_ASCII);
        AuditEventOriginVerifier verifier=new AuditEventOriginVerifier(
                true,publicPem,"xerj-origin-ci-1");
        JdbcTemplate jdbc=auditDatabase();
        assertEquals("88888888-8888-4888-8888-888888888888",
                jdbc.queryForObject("SELECT token::text FROM public.xerj_dr_fence",
                        String.class));
        assertEquals("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
                jdbc.queryForObject("""
                    SELECT tenant_id::text FROM public.audit_events
                    WHERE event_id='66666666-6666-4666-8666-666666666666'
                    """,String.class));
        AuditEventStore listener=new AuditEventStore(
                new AuditEventRepository(jdbc),verifier);
        ObjectMapper json=new ObjectMapper();
        List<String> eventIds=new ArrayList<>();
        List<byte[]> invalidBodies=new ArrayList<>();
        int applied=0,replayed=0,rejected=0;
        try(Connection connection=broker().newConnection();
            var channel=connection.createChannel()){
            try{
                for(int messageNumber=0;messageNumber<7;messageNumber++){
                    GetResponse delivered=null;
                    for(int poll=0;poll<50 && delivered==null;poll++){
                        delivered=channel.basicGet(source,false);
                        if(delivered==null)Thread.sleep(100);
                    }
                    assertNotNull(delivered,"Missing broker-published synthetic event");
                    Map<String,Object> map=json.readValue(delivered.getBody(),
                            new TypeReference<Map<String,Object>>(){});
                    assertEquals("user.created",map.get("eventType"));
                    String id=(String)map.get("eventId");
                    assertNotNull(id);
                    eventIds.add(id);
                    String kind=(String)map.get("name");
                    if(kind.startsWith("signed-")){
                        listener.consume(map);
                        channel.basicAck(delivered.getEnvelope().getDeliveryTag(),false);
                        if("signed-duplicate".equals(kind) && replayed==0){
                            applied++;
                            replayed++;
                        }else if("signed-duplicate".equals(kind)){
                            replayed++;
                        }else{
                            applied++;
                        }
                    }else{
                        var failed=assertThrows(
                                AmqpRejectAndDontRequeueException.class,
                                ()->listener.consume(map));
                        assertEquals("Rejected malformed User domain event",
                                failed.getMessage());
                        assertNull(failed.getCause());
                        assertEquals(0,jdbc.queryForObject(
                                "SELECT count(*) FROM public.audit_events WHERE event_id=?",
                                Integer.class,id));
                        invalidBodies.add(delivered.getBody());
                        channel.basicReject(delivered.getEnvelope().getDeliveryTag(),false);
                        rejected++;
                    }
                }
                assertEquals(3,applied,"Three unique signed domain events persisted");
                assertEquals(2,replayed,"Duplicate sent twice but persisted once");
                assertEquals(3,rejected,"Three invalid messages rejected");
                for(String id:eventIds){
                    int observed=jdbc.queryForObject(
                            "SELECT count(*) FROM public.audit_events WHERE event_id=?",
                            Integer.class,id);
                    assertTrue(observed==0 || observed==1);
                }
                int persisted=0;
                for(String id:eventIds.stream().distinct().toList()){
                    persisted+=jdbc.queryForObject(
                            "SELECT count(*) FROM public.audit_events WHERE event_id=?",
                            Integer.class,id);
                }
                assertEquals(3,persisted);
                // A poison is rejected with requeue=false; RabbitMQ routes the
                // *exact* original body to an isolated test-only DLQ.
                for(byte[] expected:invalidBodies){
                    GetResponse parked=null;
                    for(int poll=0;poll<40 && parked==null;poll++){
                        parked=channel.basicGet(dlq,false);
                        if(parked==null)Thread.sleep(100);
                    }
                    assertNotNull(parked);
                    byte[] quarantinedBytes=parked.getBody();
                    assertTrue(invalidBodies.stream().anyMatch(
                            body->Arrays.equals(body,quarantinedBytes)));
                    channel.basicAck(parked.getEnvelope().getDeliveryTag(),false);
                }
                assertEquals(0,channel.queueDeclarePassive(source).getMessageCount());
                assertEquals(0,channel.queueDeclarePassive(dlq).getMessageCount());
                assertEquals("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
                        jdbc.queryForObject("""
                            SELECT tenant_id::text FROM public.audit_events
                            WHERE event_id='66666666-6666-4666-8666-666666666666'
                            """,String.class));
                System.out.println(
                    "XERJ_REAL_USER_SIGNED_PRODUCER_BROKER_AUDIT_DATABASE_PASS"
                    +" accepted_unique=3 duplicate_replays=1 rejected_quarantined=3");
            }finally{
                try{
                    for(String id:eventIds){
                        jdbc.update("DELETE FROM public.audit_events WHERE event_id=?",id);
                    }
                }finally{
                    channel.queueDelete(source);
                    channel.queueDelete(dlq);
                    channel.exchangeDelete(dlx);
                    Files.deleteIfExists(pub);
                }
            }
        }
    }

}
