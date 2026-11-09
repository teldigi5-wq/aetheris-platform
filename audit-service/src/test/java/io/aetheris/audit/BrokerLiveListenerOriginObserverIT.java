package io.aetheris.audit;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.GetResponse;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Actual Spring @RabbitListener in separate running Audit JAR must ACK signed
 * User events and reject invalid events into an isolated RabbitMQ DLQ.
 * This test ONLY observes Rabbit/SQL effects; it does not invoke consume().
 */
class BrokerLiveListenerOriginObserverIT {
    private static String env(String n) {
        String value=System.getenv(n);
        if(value==null || value.isBlank())throw new IllegalStateException("Missing CI-only fixture: "+n);
        return value;
    }
    private static void guard() {
        String id=env("GITHUB_RUN_ID"),attempt=env("GITHUB_RUN_ATTEMPT");
        assertEquals("true",env("GITHUB_ACTIONS"));
        assertTrue(id.matches("[0-9]+") && attempt.matches("[0-9]+"));
        assertEquals("xerj-mtls-ci-"+id+"-"+attempt,env("XERJ_COMPOSE_PROJECT"));
        assertEquals("xerj_origin_listener",env("XERJ_ORIGIN_LISTENER_VHOST"));
        assertTrue(env("RUNNER_TEMP").startsWith("/home/runner/"));
    }
    private static JdbcTemplate db() {
        var ds=new PGSimpleDataSource();
        ds.setURL("jdbc:postgresql://127.0.0.1:55432/xerj_fence_audit"
                +"?connectTimeout=3&socketTimeout=5");
        ds.setUser("xerj_ci");
        ds.setPassword(env("XERJ_TEST_PG_PASSWORD"));
        return new JdbcTemplate(ds);
    }
    private static ConnectionFactory broker() {
        var cf=new ConnectionFactory();
        cf.setHost("127.0.0.1");
        cf.setPort(55672);
        cf.setVirtualHost(env("XERJ_ORIGIN_LISTENER_VHOST"));
        cf.setUsername("xerj_ci"); // observer test only, NOT real Audit JAR
        cf.setPassword(env("XERJ_TEST_RABBIT_PASSWORD"));
        cf.setConnectionTimeout(5000);
        return cf;
    }
    private static int count(JdbcTemplate jdbc,String name) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM public.audit_events WHERE name=?",
                Integer.class,name);
    }
    @Test
    void realSpringConsumerWithRestrictedReadOnlyBrokerAccountPersistsAndQuarantines()
            throws Exception {
        guard();
        var names=Files.readAllLines(Path.of(env("XERJ_ORIGIN_NAMES_PATH")),
                StandardCharsets.US_ASCII);
        assertEquals(7,names.size(),"Expected fixture names and dedicated DLQ");
        for(int i=0;i<6;i++)assertTrue(names.get(i).matches(
                "xerj-live-[0-9]+-[0-9]+-[a-z-]+"));
        String source="aetheris.audit.user-events";
        String dlq=names.get(6);
        var jdbc=db();
        assertEquals("88888888-8888-4888-8888-888888888888",
                jdbc.queryForObject("SELECT token::text FROM public.xerj_dr_fence",
                        String.class));
        var expectedInvalid=Set.of(names.get(3),names.get(4),names.get(5));
        try(var connection=broker().newConnection();
            var channel=connection.createChannel()){
            // This is a live application consumer with asynchronous dispatch.
            // Wait for real DB commit + broker poison quarantine, not sleeps.
            boolean ready=false;
            for(int i=0;i<100;i++){
                int accepted=count(jdbc,names.get(0))
                        +count(jdbc,names.get(1))
                        +count(jdbc,names.get(2));
                int dead=channel.queueDeclarePassive(dlq).getMessageCount();
                int pending=channel.queueDeclarePassive(source).getMessageCount();
                if(accepted==3 && dead==3 && pending==0){
                    ready=true;
                    break;
                }
                Thread.sleep(200);
            }
            assertTrue(ready,
                    "Live Spring listener did not persist 3 unique events and quarantine 3 invalid events");
            for(int i=0;i<3;i++)assertEquals(1,count(jdbc,names.get(i)));
            for(int i=3;i<6;i++)assertEquals(0,count(jdbc,names.get(i)));
            assertEquals("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
                    jdbc.queryForObject(
                        "SELECT tenant_id::text FROM audit_events WHERE name=?",
                        String.class,names.get(0)));
            assertNull(jdbc.queryForObject(
                    "SELECT tenant_id::text FROM audit_events WHERE name=?",
                    String.class,names.get(2)));
            var json=new ObjectMapper();
            Set<String> deniedNames=new HashSet<>();
            for(int i=0;i<3;i++){
                GetResponse msg=channel.basicGet(dlq,false);
                assertNotNull(msg,"Real listener must dead-letter all three poisoned events");
                Map<String,Object> fields=json.readValue(msg.getBody(),
                        new TypeReference<Map<String,Object>>(){});
                String name=(String)fields.get("name");
                assertTrue(expectedInvalid.contains(name),"Unexpected DLQ message provenance");
                assertTrue(deniedNames.add(name),"Duplicate/missing poison dispatch");
                channel.basicAck(msg.getEnvelope().getDeliveryTag(),false);
            }
            assertEquals(expectedInvalid,deniedNames);
            assertEquals(0,channel.queueDeclarePassive(source).getMessageCount());
            assertEquals(0,channel.queueDeclarePassive(dlq).getMessageCount());
            // Prove existing synthetic source row is untouched.
            assertEquals("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
                    jdbc.queryForObject("""
                        SELECT tenant_id::text FROM audit_events
                        WHERE event_id='66666666-6666-4666-8666-666666666666'
                        """,String.class));
            System.out.println(
                "XERJ_REAL_SPRING_AUDIT_RABBIT_LISTENER_RSA_RESTRICTED_CONSUMER_PASS"
                +" signed_unique=3 idempotent_duplicate=1 rejected_dlq=3");
        } finally {
            // This query can affect only the six run-unique synthetic names in
            // an explicitly fenced, disposable Audit PostgreSQL instance.
            for(int i=0;i<6;i++){
                jdbc.update("DELETE FROM public.audit_events WHERE name=?",names.get(i));
            }
        }
    }
}
