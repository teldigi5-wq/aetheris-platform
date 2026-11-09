package io.aetheris.audit;

import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Real PostgreSQL privilege-separation acceptance. The Audit listener must
 * write audit_events and execute the locked membership decision but MUST NOT
 * update/read raw membership rows. The reconciler must be unable to read or
 * tamper with audit_events, and has no schema ownership/DDL privileges.
 */
class AuditPostgresRoleIsolationIT {
    private static String env(String key) {
        String v=System.getenv(key);
        if(v==null||v.isBlank())throw new IllegalStateException("Missing disposable fixture "+key);
        return v;
    }
    private static void guard() {
        String run=env("GITHUB_RUN_ID"),attempt=env("GITHUB_RUN_ATTEMPT");
        assertEquals("true",env("GITHUB_ACTIONS"));
        assertTrue(run.matches("[0-9]+")&&attempt.matches("[0-9]+"));
        assertEquals("xerj-mtls-ci-"+run+"-"+attempt,env("XERJ_COMPOSE_PROJECT"));
        assertTrue(env("RUNNER_TEMP").startsWith("/home/runner/"));
    }
    private static String url() {
        return "jdbc:postgresql://127.0.0.1:55432/xerj_fence_audit"
                +"?connectTimeout=3&socketTimeout=5";
    }
    private static PGSimpleDataSource ds(String username,String password) {
        var ds=new PGSimpleDataSource();
        ds.setURL(url());
        ds.setUser(username);
        ds.setPassword(password);
        return ds;
    }
    private static JdbcTemplate jdbc(String username,String password) {
        return new JdbcTemplate(ds(username,password));
    }
    private static void denied(JdbcTemplate db,String sql,Object... args) {
        var err=assertThrows(DataAccessException.class,()->db.update(sql,args));
        Throwable c=err;
        boolean forbidden=false;
        while(c!=null){
            if(c instanceof SQLException s && "42501".equals(s.getSQLState())){
                forbidden=true;
                break;
            }
            c=c.getCause();
        }
        assertTrue(forbidden,"Only PostgreSQL insufficient_privilege (42501) passes");
    }
    private static KeyPair newKey() throws Exception {
        var g=KeyPairGenerator.getInstance("RSA");
        g.initialize(3072);
        return g.generateKeyPair();
    }
    private static String publicPem(KeyPair keys) {
        return "-----BEGIN PUBLIC KEY-----\n"
                +Base64.getMimeEncoder(64,new byte[]{'\n'})
                    .encodeToString(keys.getPublic().getEncoded())
                +"\n-----END PUBLIC KEY-----";
    }
    private static String signed(KeyPair keys,UUID tenant,long user,
                                 String status,long revision,Instant since) {
        Instant now=Instant.now();
        return Jwts.builder()
                .issuer(AuditSignedMembershipImporter.ISSUER)
                .audience().add(AuditSignedMembershipImporter.AUDIENCE).and()
                .subject(AuditSignedMembershipImporter.SUBJECT)
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(now.minusSeconds(1)))
                .expiration(Date.from(now.plusSeconds(180)))
                .claim("tenantId",tenant.toString())
                .claim("userId",user)
                .claim("status",status)
                .claim("revision",revision)
                .claim("validSince",since.toString())
                .header().keyId("role-isolated-synthetic-authority").and()
                .signWith(keys.getPrivate(),Jwts.SIG.RS256).compact();
    }

    @Test
    void actualAuditAndImporterJDBCPrincipalsEnforceDistinctPrivileges()
            throws Exception {
        guard();
        var auditDs=ds("xerj_audit_ingest",env("XERJ_AUDIT_INGEST_DB_PASSWORD"));
        var ingest=new JdbcTemplate(auditDs);
        var writer=jdbc("xerj_membership_writer",env("XERJ_MEMBERSHIP_WRITER_DB_PASSWORD"));
        var admin=jdbc("xerj_ci",env("XERJ_TEST_PG_PASSWORD"));
        assertEquals("xerj_audit_ingest",
                ingest.queryForObject("SELECT current_user",String.class));
        assertEquals("xerj_membership_writer",
                writer.queryForObject("SELECT current_user",String.class));
        assertEquals("88888888-8888-4888-8888-888888888888",
                admin.queryForObject("SELECT token::text FROM public.xerj_dr_fence",
                        String.class));

        UUID tenant=UUID.randomUUID();
        String eventId=UUID.randomUUID().toString();
        long user=2500042L;
        var keys=newKey();
        try{
            // Production-style Spring constructor uses a separate JDBC writer
            // identity and does not create another auto-configured datasource.
            var importer=new AuditSignedMembershipImporter(
                    true, publicPem(keys), "role-isolated-synthetic-authority",
                    "unrelated-user-event-kid", ingest, url(),
                    "xerj_membership_writer",
                    env("XERJ_MEMBERSHIP_WRITER_DB_PASSWORD"),url(),
                    "xerj_audit_ingest");
            importer.importSigned(signed(keys,tenant,user,"ACTIVE",1,
                    Instant.now().minusSeconds(240)));
            Boolean allowed=new TransactionTemplate(
                    new DataSourceTransactionManager(auditDs)).execute(s->
                    ingest.queryForObject(
                        "SELECT public.audit_membership_can_ingest(?,?,?)",
                        Boolean.class,tenant,user,Timestamp.from(Instant.now())));
            assertEquals(Boolean.TRUE,allowed,
                    "Only the SECURITY DEFINER function may lock as Audit role");
            denied(ingest,
                    "UPDATE public.audit_user_tenant_membership_fence SET status='REVOKED' WHERE tenant_id=?",
                    tenant);
            denied(ingest,
                    "INSERT INTO public.audit_user_tenant_membership_fence "
                            +"(tenant_id,user_id,status,valid_since,revision) "
                            +"VALUES (?,?,?,CURRENT_TIMESTAMP,1)",
                    UUID.randomUUID(),user,"ACTIVE");
            denied(ingest,
                    "DELETE FROM public.audit_user_tenant_membership_fence WHERE tenant_id=?",
                    tenant);
            assertEquals(1L,admin.queryForObject(
                    "SELECT revision FROM public.audit_user_tenant_membership_fence "
                            +"WHERE tenant_id=? AND user_id=?",
                    Long.class,tenant,user));

            // Audit ingest can write its event, not change membership.
            var event=new UserDomainEvent(eventId,"user.created",
                    Instant.now(),user,"Synthetic limited Audit",
                    "limited-audit@example.test",tenant);
            new TransactionTemplate(new DataSourceTransactionManager(auditDs))
                    .executeWithoutResult(s->
                        new AuditEventRepository(ingest).saveIfAbsent(event));
            assertEquals(1,admin.queryForObject(
                    "SELECT count(*) FROM public.audit_events WHERE event_id=?",
                    Integer.class,eventId));
            denied(writer,"DELETE FROM public.audit_events WHERE event_id=?",eventId);
            denied(writer,
                    "INSERT INTO public.audit_events "
                            +"(event_id,event_type,occurred_at,user_id,name,email) "
                            +"VALUES (?,'user.created',CURRENT_TIMESTAMP,1,'bad','bad@example.test')",
                    UUID.randomUUID().toString());
            // Writer can only modify the membership mirror, not Audit events.
            assertThrows(DataAccessException.class,
                    ()->writer.queryForObject(
                        "SELECT count(*) FROM public.audit_events",Integer.class));
            assertEquals("ACTIVE",admin.queryForObject(
                    "SELECT status FROM public.audit_user_tenant_membership_fence "
                            +"WHERE tenant_id=? AND user_id=?",
                    String.class,tenant,user));
            importer.importSigned(signed(keys,tenant,user,"REVOKED",2,
                    Instant.now().minusSeconds(20)));
            allowed=new TransactionTemplate(
                    new DataSourceTransactionManager(auditDs)).execute(s->
                    ingest.queryForObject(
                        "SELECT public.audit_membership_can_ingest(?,?,?)",
                        Boolean.class,tenant,user,Timestamp.from(Instant.now())));
            assertEquals(Boolean.FALSE,allowed,
                    "Revocation after signed import is denied by actual ingest role");
            System.out.println(
                    "XERJ_AUDIT_POSTGRES_INGEST_WRITER_ROLE_SEPARATION_PASS");
        }finally{
            admin.update("DELETE FROM public.audit_events WHERE event_id=?",eventId);
            admin.update("DELETE FROM public.audit_user_tenant_membership_fence "
                    +"WHERE tenant_id=? AND user_id=?",tenant,user);
        }
    }

    @Test
    void springBootstrapRefusesMissingSharedOrWrongMembershipWriterCredentials()
            throws Exception {
        guard();
        var ingest=jdbc("xerj_audit_ingest",env("XERJ_AUDIT_INGEST_DB_PASSWORD"));
        var keys=newKey();String pem=publicPem(keys);
        assertThrows(IllegalArgumentException.class,()->
                new AuditSignedMembershipImporter(
                        true,pem,"role-isolated-synthetic-authority","event-kid",
                        ingest,"","xerj_membership_writer",
                        env("XERJ_MEMBERSHIP_WRITER_DB_PASSWORD"),url(),
                        "xerj_audit_ingest"));
        assertThrows(IllegalArgumentException.class,()->
                new AuditSignedMembershipImporter(
                        true,pem,"role-isolated-synthetic-authority","event-kid",
                        ingest,url(),"xerj_audit_ingest",
                        env("XERJ_AUDIT_INGEST_DB_PASSWORD"),url(),
                        "xerj_audit_ingest"));
        assertThrows(IllegalArgumentException.class,()->
                new AuditSignedMembershipImporter(
                        false,"","","event-kid",ingest,url(),
                        "xerj_membership_writer",
                        env("XERJ_MEMBERSHIP_WRITER_DB_PASSWORD"),url(),
                        "xerj_audit_ingest"));
        System.out.println(
                "XERJ_AUDIT_MEMBERSHIP_SEPARATE_DB_ROLE_CONFIG_FAIL_CLOSED_PASS");
    }
}
