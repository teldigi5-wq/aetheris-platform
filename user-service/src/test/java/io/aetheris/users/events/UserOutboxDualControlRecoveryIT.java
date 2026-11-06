package io.aetheris.users.events;

import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** Strictly synthetic, unique-hosted-Postgres 16 two-role approval and replay. */
class UserOutboxDualControlRecoveryIT {
    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing isolated CI fixture: " + name);
        }
        return value;
    }
    private static PGSimpleDataSource source(String user, String password) {
        PGSimpleDataSource data = new PGSimpleDataSource();
        data.setURL("jdbc:postgresql://127.0.0.1:55432/xerj_ci");
        data.setUser(user);
        data.setPassword(password);
        return data;
    }
    private static void park(JdbcTemplate db, UUID id) {
        assertEquals(1, db.update("""
                UPDATE public.user_event_outbox
                SET state='parked', attempts=12,
                    lease_token=NULL, lease_until=NULL
                WHERE event_id=?
                """, id));
    }
    private static UUID approve(JdbcTemplate approver, UUID id, String ticket) {
        return approver.queryForObject(
                "SELECT public.approve_parked_user_outbox_replay(?,?)",
                UUID.class, id, ticket);
    }
    private static UUID rearm(JdbcTemplate operator, UUID id) {
        return operator.queryForObject(
                "SELECT public.rearm_approved_parked_user_outbox(?)",
                UUID.class, id);
    }

    @Test
    void independentApprovalAndOperatorSingleUseCASAuditAndStrictTwoRecoveryCap()
            throws Exception {
        assertTrue(required("XERJ_COMPOSE_PROJECT").matches("xerj-mtls-ci-[0-9]+-[0-9]+"));
        var adminSource = source("xerj_ci", required("XERJ_TEST_PG_PASSWORD"));
        var admin = new JdbcTemplate(adminSource);
        var approver = new JdbcTemplate(source("xerj_outbox_approver",
                required("XERJ_OUTBOX_APPROVER_PASSWORD")));
        var operator = new JdbcTemplate(source("xerj_outbox_operator",
                required("XERJ_OUTBOX_OPERATOR_PASSWORD")));
        var store = new UserOutboxStore(admin);
        var tx = new TransactionTemplate(new DataSourceTransactionManager(adminSource));

        UUID event = tx.execute(status -> store.append(
                "user.created", 649011L, "Synthetic operator case",
                "operator-case@example.test", null));
        UUID expired = tx.execute(status -> store.append(
                "user.deleted", 649012L, "Synthetic expiry case",
                "expired-approval@example.test", null));
        UUID race = tx.execute(status -> store.append(
                "user.created", 649013L, "Synthetic concurrent case",
                "concurrent-approval@example.test", null));
        assertNotNull(event);
        assertNotNull(expired);
        assertNotNull(race);
        List<UUID> ids = List.of(event, expired, race);
        try {
            // Separate login roles cannot read or mutate original PII tables.
            assertThrows(DataAccessException.class,
                    () -> approver.queryForObject(
                            "SELECT count(*) FROM public.user_event_outbox",Long.class));
            assertThrows(DataAccessException.class,
                    () -> operator.queryForObject(
                            "SELECT count(*) FROM public.user_event_outbox",Long.class));
            assertThrows(DataAccessException.class,
                    () -> operator.update(
                            "UPDATE public.user_event_outbox SET state='retry' WHERE event_id=?",
                            event));
            assertThrows(DataAccessException.class,
                    () -> approve(operator,event,"SEC-20261106-1001"));
            assertThrows(DataAccessException.class,
                    () -> rearm(approver,event));
            assertThrows(DataAccessException.class,
                    () -> rearm(operator,event));
            assertThrows(DataAccessException.class,
                    () -> approve(approver,UUID.randomUUID(),"SEC-20261106-1001"));
            assertThrows(DataAccessException.class,
                    () -> approve(approver,event,"not-an-approved-ticket"));

            park(admin,event);
            UUID approval = approve(approver,event,"SEC-20261106-1001");
            assertNotNull(approval);
            assertThrows(DataAccessException.class,
                    () -> approve(approver,event,"SEC-20261106-1001"),
                    "One approval only per recovery generation");
            UUID audit = rearm(operator,event);
            assertNotNull(audit);
            assertEquals("retry",admin.queryForObject(
                    "SELECT state FROM public.user_event_outbox WHERE event_id=?",
                    String.class,event));
            assertEquals(0,admin.queryForObject(
                    "SELECT attempts FROM public.user_event_outbox WHERE event_id=?",
                    Integer.class,event));
            assertEquals(1,admin.queryForObject(
                    "SELECT recovery_generation FROM public.user_event_outbox WHERE event_id=?",
                    Integer.class,event));
            assertEquals(1,admin.queryForObject(
                    "SELECT count(*) FROM public.user_outbox_replay_audit WHERE event_id=?",
                    Integer.class,event));
            assertEquals("xerj_outbox_approver",admin.queryForObject(
                    "SELECT approved_by FROM public.user_outbox_replay_audit WHERE audit_id=?",
                    String.class,audit));
            assertEquals("xerj_outbox_operator",admin.queryForObject(
                    "SELECT executed_by FROM public.user_outbox_replay_audit WHERE audit_id=?",
                    String.class,audit));
            assertThrows(DataAccessException.class,() -> rearm(operator,event),
                    "Same approval cannot authorize another replay");

            // Re-park after another 12 failures. New generation needs a NEW
            // approval, then a second successful recovery exhausts the cap.
            park(admin,event);
            assertThrows(DataAccessException.class,() -> rearm(operator,event));
            UUID secondApproval=approve(approver,event,"INC-20261106-2001");
            assertNotEquals(approval,secondApproval);
            UUID secondAudit=rearm(operator,event);
            assertNotEquals(audit,secondAudit);
            assertEquals(2,admin.queryForObject(
                    "SELECT recovery_generation FROM public.user_event_outbox WHERE event_id=?",
                    Integer.class,event));
            assertEquals(2,admin.queryForObject(
                    "SELECT count(*) FROM public.user_outbox_replay_audit WHERE event_id=?",
                    Integer.class,event));
            park(admin,event);
            assertThrows(DataAccessException.class,
                    () -> approve(approver,event,"INC-20261106-3001"),
                    "A third recovery is forbidden even with a fresh ticket");

            // Expired evidence cannot be consumed; no update or audit allowed.
            park(admin,expired);
            UUID expApproval=approve(approver,expired,"SEC-20261106-1002");
            admin.update("""
                    UPDATE public.user_outbox_replay_approvals
                    SET approved_at=clock_timestamp()-INTERVAL '20 minutes',
                        expires_at=clock_timestamp()-INTERVAL '5 minutes'
                    WHERE approval_id=?
                    """,expApproval);
            assertThrows(DataAccessException.class,()->rearm(operator,expired));
            assertEquals("parked",admin.queryForObject(
                    "SELECT state FROM public.user_event_outbox WHERE event_id=?",
                    String.class,expired));
            assertEquals(0,admin.queryForObject(
                    "SELECT count(*) FROM public.user_outbox_replay_audit WHERE event_id=?",
                    Integer.class,expired));

            // Competing operators cannot replay same ticket twice.
            park(admin,race);
            approve(approver,race,"SEC-20261106-1003");
            var start = new CountDownLatch(1);
            var executor = Executors.newFixedThreadPool(2);
            try {
                var futures = new ArrayList<java.util.concurrent.Future<Boolean>>();
                for(int n=0;n<2;n++){
                    futures.add(executor.submit(() -> {
                        var independent=new JdbcTemplate(source("xerj_outbox_operator",
                                required("XERJ_OUTBOX_OPERATOR_PASSWORD")));
                        assertTrue(start.await(10,TimeUnit.SECONDS));
                        try {rearm(independent,race);return true;}
                        catch(DataAccessException denied){return false;}
                    }));
                }
                start.countDown();
                int successful=0;
                for(var future:futures) if(future.get(20,TimeUnit.SECONDS))successful++;
                assertEquals(1,successful,"Concurrent operators must not double-replay");
            } finally {
                executor.shutdownNow();
                assertTrue(executor.awaitTermination(10,TimeUnit.SECONDS));
            }
            assertEquals(1,admin.queryForObject(
                    "SELECT count(*) FROM public.user_outbox_replay_audit WHERE event_id=?",
                    Integer.class,race));
            System.out.println("XERJ_REAL_POSTGRES_OUTBOX_DUAL_CONTROL_SINGLE_ID_RECOVERY_PASS");
            System.out.println("XERJ_OUTBOX_REPLAY_DENIAL_EXPIRY_RACE_AND_CAP_PASS");
        } finally {
            for(UUID id:ids) {
                admin.update("DELETE FROM public.user_outbox_replay_audit WHERE event_id=?",id);
                admin.update("DELETE FROM public.user_outbox_replay_approvals WHERE event_id=?",id);
                admin.update("DELETE FROM public.user_event_outbox WHERE event_id=?",id);
            }
        }
    }
}
