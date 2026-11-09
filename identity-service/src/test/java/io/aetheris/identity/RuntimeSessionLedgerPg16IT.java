package io.aetheris.identity;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * S0-E6J review-only actual PostgreSQL16 role/DDL/transaction acceptance.
 * Must ONLY execute against a newly created disposable GitHub Actions DB.
 * No production migration, real Identity credentials, live bearer, listener,
 * HTTP route, customer data, key or Deployment is used or altered.
 */
class RuntimeSessionLedgerPg16IT {
    private static final String URL =
        "jdbc:postgresql://127.0.0.1:5432/aetheris_e6j_disposable";
    private static final String ISS = "https://identity.aetheris.example.invalid";
    private static final String KID = "s0e6j-review-signer";
    private static final String PREFIX = "e6j_runtime_auth.";
    private static final String ENROLL = "SELECT " + PREFIX
        + "enroll_signed_session(?,?,?,?,?,?,?)";
    private static final String REVOKE = "SELECT " + PREFIX
        + "revoke_signed_session(?,?,?,?)";

    @BeforeAll
    static void createDisposableLedger() throws Exception {
        Assumptions.assumeTrue("ONLY_DISPOSABLE_E6J_POSTGRES".equals(
            System.getenv("AETHERIS_E6J_DISPOSABLE_MARKER")),
            "No E6J test may run against a normal database");
        assertEquals("true",System.getenv("GITHUB_ACTIONS"));
        assertEquals("127.0.0.1",System.getenv("PGHOST"));
        assertEquals("aetheris_e6j_disposable",System.getenv("PGDATABASE"));
        assertEquals("e6j_test_only",System.getenv("PGUSER"));
        assertNotNull(System.getenv("PGPASSWORD"));
        try(InputStream stream=RuntimeSessionLedgerPg16IT.class
                .getResourceAsStream("/security/e6j_runtime_session_ledger_pg16.sql")) {
            assertNotNull(stream, "Versioned review DDL must exist");
            String ddl=new String(stream.readAllBytes(),StandardCharsets.UTF_8);
            assertTrue(ddl.startsWith("-- S0-E6J. DISPOSABLE"));
            try(Connection c=admin();Statement sql=c.createStatement()) {
                // Driver sends the complete SQL script; DO NOT split PL/pgSQL
                // function bodies on semicolons.
                sql.execute(ddl);
                sql.execute("INSERT INTO "+PREFIX+"signer_key_states "+
                    "(issuer,kid,state) VALUES ('"+ISS+"','"+KID+"','ACTIVE')");
                sql.execute("INSERT INTO "+PREFIX+"principal_epochs "+
                    "(principal_id,reject_issued_at_or_before_epoch) "+
                    "VALUES (42,0),(77,0)");
            }
        }
    }

    @AfterAll
    static void dropOnlyDisposableLedger() throws Exception {
        if(!"ONLY_DISPOSABLE_E6J_POSTGRES".equals(
                System.getenv("AETHERIS_E6J_DISPOSABLE_MARKER"))) return;
        try(Connection c=admin();Statement s=c.createStatement()) {
            s.execute("DROP SCHEMA IF EXISTS e6j_runtime_auth CASCADE");
            s.execute("DROP ROLE IF EXISTS e6j_reader");
            s.execute("DROP ROLE IF EXISTS e6j_exec_owner");
            s.execute("DROP ROLE IF EXISTS e6j_issuer");
            s.execute("DROP ROLE IF EXISTS e6j_revoker");
            s.execute("DROP ROLE IF EXISTS e6j_outsider");
            try(var rows=s.executeQuery("""
                SELECT count(*) FROM pg_namespace WHERE nspname='e6j_runtime_auth'
                """)) {
                assertTrue(rows.next());
                assertEquals(0,rows.getInt(1));
            }
        }
    }

    private static Connection admin() throws SQLException {
        return DriverManager.getConnection(URL,"e6j_test_only",
            System.getenv("PGPASSWORD"));
    }

    private static Connection tx(String effectiveRole) throws SQLException {
        Connection c=admin();
        c.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
        c.setAutoCommit(false);
        try(Statement sql=c.createStatement()) {
            sql.execute("SET LOCAL ROLE "+effectiveRole);
        } catch(SQLException problem) {
            c.close();throw problem;
        }
        return c;
    }

    private static String jti() {return "e6j_"+UUID.randomUUID();}
    private static long now() {return Instant.now().getEpochSecond();}

    private static boolean enroll(Connection c,String kid,String id,long uid,
                                  long iat,long exp,UUID request) throws SQLException {
        try(PreparedStatement p=c.prepareStatement(ENROLL)) {
            p.setString(1,ISS);p.setString(2,kid);p.setString(3,id);
            p.setLong(4,uid);p.setLong(5,iat);p.setLong(6,exp);
            p.setObject(7,request);
            try(var rows=p.executeQuery()) {
                assertTrue(rows.next());boolean result=rows.getBoolean(1);
                assertFalse(rows.next());return result;
            }
        }
    }

    private static boolean revoke(Connection c,String id,UUID request)
            throws SQLException {
        try(PreparedStatement p=c.prepareStatement(REVOKE)) {
            p.setString(1,ISS);p.setString(2,KID);p.setString(3,id);
            p.setObject(4,request);
            try(var rows=p.executeQuery()) {
                assertTrue(rows.next());return rows.getBoolean(1);
            }
        }
    }

    private static void enrolled(String id,long uid,long iat) throws SQLException {
        try(Connection c=tx("e6j_issuer")) {
            assertTrue(enroll(c,KID,id,uid,iat,iat+180,UUID.randomUUID()));
            c.commit();
        }
    }

    // Runtime uses this restricted SECURITY DEFINER lookup, not raw table
    // SELECT ... FOR SHARE, which would need elevated table privileges.
    private static boolean active(Connection c,String id,long uid,long iat)
            throws SQLException {
        try(PreparedStatement p=c.prepareStatement(
                "SELECT "+PREFIX+"check_signed_session(?,?,?,?,?)")) {
            p.setString(1,ISS);p.setString(2,KID);p.setString(3,id);
            p.setLong(4,uid);p.setLong(5,iat);
            try(var rows=p.executeQuery()) {
                assertTrue(rows.next());
                boolean granted=rows.getBoolean(1);
                assertFalse(rows.next());
                return granted;
            }
        }
    }

    @Test
    void independentlyRestrictedReaderCannotWriteTablesOrCallIssuer()
            throws Exception {
        long t=now();String id=jti();
        try(Connection reader=tx("e6j_reader")) {
            assertFalse(active(reader,id,42,t));
            SQLException direct=assertThrows(SQLException.class,
                ()->reader.createStatement().execute(
                    "DELETE FROM "+PREFIX+"session_registrations"));
            assertEquals("42501",direct.getSQLState());
            reader.rollback();
        }
        try(Connection reader=tx("e6j_reader")) {
            SQLException rawSelect=assertThrows(SQLException.class,
                ()->reader.createStatement().execute(
                    "SELECT * FROM "+PREFIX+"session_registrations"));
            assertEquals("42501",rawSelect.getSQLState());
            reader.rollback();
        }
        try(Connection reader=tx("e6j_reader")) {
            SQLException indirect=assertThrows(SQLException.class,
                ()->enroll(reader,KID,id,42,t,t+180,UUID.randomUUID()));
            assertEquals("42501",indirect.getSQLState());
            reader.rollback();
        }
        try(Connection outsider=tx("e6j_outsider")) {
            SQLException denied=assertThrows(SQLException.class,
                ()->active(outsider,id,42,t));
            assertEquals("42501",denied.getSQLState());
            outsider.rollback();
        }
    }

    @Test
    void issuerCreatesDurableExactSessionAndRevokerCannotIssue() throws Exception {
        long t=now();String id=jti();
        enrolled(id,42,t);
        try(Connection reader=tx("e6j_reader")) {
            assertTrue(active(reader,id,42,t));
            assertFalse(active(reader,id,77,t));
            assertFalse(active(reader,id,42,t+1));
            reader.commit();
        }
        try(Connection revoker=tx("e6j_revoker")) {
            assertThrows(SQLException.class,()->enroll(
                revoker,KID,jti(),42,t,t+180,UUID.randomUUID()));
            revoker.rollback();
        }
        try(Connection c=tx("e6j_revoker")) {
            assertTrue(revoke(c,id,UUID.randomUUID()));
            assertFalse(revoke(c,id,UUID.randomUUID()),
                "Revoked session cannot be un-revoked or mutated by replay");
            c.commit();
        }
        try(Connection reader=tx("e6j_reader")) {
            assertFalse(active(reader,id,42,t));
            reader.commit();
        }
    }

    @Test
    void malformedExpiredAndUntrustedSigningKeysNeverEnroll() throws Exception {
        long t=now();
        try(Connection issuer=tx("e6j_issuer")) {
            assertFalse(enroll(issuer,KID,jti(),42,t,t+301,UUID.randomUUID()));
            assertFalse(enroll(issuer,KID,jti(),42,t,t-1,UUID.randomUUID()));
            assertFalse(enroll(issuer,KID,jti(),42,t-500,t-10,UUID.randomUUID()));
            assertFalse(enroll(issuer,"unknown-signer",jti(),42,t,t+180,UUID.randomUUID()));
            assertFalse(enroll(issuer,KID,"short",42,t,t+180,UUID.randomUUID()));
            assertFalse(enroll(issuer,KID,jti(),999,t,t+180,UUID.randomUUID()));
            issuer.rollback();
        }
    }

    @Test
    void duplicateAuditRequestRollsBackSecurityMutationAtomically()
            throws Exception {
        long t=now();String id=jti();UUID used=UUID.randomUUID();
        try(Connection issuer=tx("e6j_issuer")) {
            assertTrue(enroll(issuer,KID,id,42,t,t+180,used));
            issuer.commit();
        }
        try(Connection revoker=tx("e6j_revoker")) {
            SQLException duplicate=assertThrows(SQLException.class,
                ()->revoke(revoker,id,used));
            assertEquals("23505",duplicate.getSQLState());
            revoker.rollback();
        }
        try(Connection reader=tx("e6j_reader")) {
            assertTrue(active(reader,id,42,t),
                "Audit collision must roll back the revocation update");
            reader.commit();
        }
    }

    @Test
    void signerAndEpochRevocationsAreStrictlyOneWay() throws Exception {
        long t=now();String id=jti();enrolled(id,42,t);
        // Rollback final mutations to keep all independent tests isolated.
        try(Connection r=tx("e6j_revoker")) {
            try(PreparedStatement s=r.prepareStatement(
                "SELECT "+PREFIX+"advance_principal_epoch(?,?,?)")) {
                s.setLong(1,42);s.setLong(2,t);s.setObject(3,UUID.randomUUID());
                try(var rows=s.executeQuery()) {
                    assertTrue(rows.next());assertTrue(rows.getBoolean(1));
                }
            }
            // The UPDATE holds the epoch row lock. An independent reader
            // would correctly block until commit; do not deadlock test thread.
            r.commit();
        }
        try(Connection reader=tx("e6j_reader")) {
            assertFalse(active(reader,id,42,t));
            reader.commit();
        }
        try(Connection revoker=tx("e6j_revoker")) {
            try(PreparedStatement p=revoker.prepareStatement(
                    "SELECT "+PREFIX+"advance_principal_epoch(?,?,?)")) {
                p.setLong(1,42);p.setLong(2,t-1);
                p.setObject(3,UUID.randomUUID());
                try(var rows=p.executeQuery()) {
                    assertTrue(rows.next());assertFalse(rows.getBoolean(1));
                }
            }
            revoker.rollback();
        }
        // Restore test isolation using only the disposable schema owner;
        // a real revoker deliberately cannot lower the monotonic cutoff.
        try(Connection owner=admin();Statement st=owner.createStatement()) {
            st.execute("UPDATE "+PREFIX+"principal_epochs "+
                "SET reject_issued_at_or_before_epoch=0 WHERE principal_id=42");
        }
        try(Connection revoker=tx("e6j_revoker")) {
            try(PreparedStatement p=revoker.prepareStatement(
                "SELECT "+PREFIX+"block_signing_key(?,?,?)")) {
                p.setString(1,ISS);p.setString(2,KID);
                p.setObject(3,UUID.randomUUID());
                try(var rows=p.executeQuery()) {
                    assertTrue(rows.next());assertTrue(rows.getBoolean(1));
                }
            }
            revoker.commit();
        }
        try(Connection reader=tx("e6j_reader")) {
            assertFalse(active(reader,id,42,t));reader.commit();
        }
        // This is a disposable test reset by schema owner, not a production
        // revoker ability or an allowed signer key reactivation sequence.
        try(Connection owner=admin();Statement st=owner.createStatement()) {
            st.execute("UPDATE "+PREFIX+"signer_key_states SET state='ACTIVE'");
        }
    }

    @Test
    void readerRowLockBlocksConcurrentRevokerInsteadOfStaleAllow()
            throws Exception {
        long t=now();String id=jti();enrolled(id,77,t);
        try(Connection reader=tx("e6j_reader");
            Connection revoker=tx("e6j_revoker")) {
            assertTrue(active(reader,id,77,t)); // FOR SHARE held.
            try(Statement s=revoker.createStatement()) {
                s.execute("SET LOCAL statement_timeout='500ms'");
            }
            SQLException blocked=assertThrows(SQLException.class,
                ()->revoke(revoker,id,UUID.randomUUID()));
            assertEquals("57014",blocked.getSQLState());
            revoker.rollback();
            reader.commit();
        }
        try(Connection revoker=tx("e6j_revoker")) {
            assertTrue(revoke(revoker,id,UUID.randomUUID()));
            revoker.commit();
        }
        try(Connection reader=tx("e6j_reader")) {
            assertFalse(active(reader,id,77,t));reader.commit();
        }
    }
}
