package io.aetheris.audit;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * The network-facing Spring Audit service must never possess the separate
 * membership writer connection or turn into a reconciliation worker.
 */
class AuditMembershipProcessSeparationTest {
    private static final String AUDIT_URL =
            "jdbc:postgresql://127.0.0.1:55432/xerj_fence_audit";
    private static final String WRITER_PASSWORD = "synthetic-32chars-only-not-real-secret";

    @Test
    void networkFacingAuditProcessCannotEnableImporterEvenWithSeparateWriterRole() {
        JdbcTemplate auditJdbc = mock(JdbcTemplate.class);
        var rejected = assertThrows(IllegalStateException.class,
                () -> new AuditSignedMembershipImporter(
                        true, "", "synthetic-membership-key",
                        "synthetic-user-event-key",
                        auditJdbc, AUDIT_URL, "xerj_membership_writer",
                        WRITER_PASSWORD, AUDIT_URL, "xerj_audit_ingest"));
        assertEquals("Audit process cannot host membership reconciliation",
                rejected.getMessage());
    }

    @Test
    void networkFacingAuditProcessRejectsWriterSecretsWhileFeatureDisabled() {
        JdbcTemplate auditJdbc = mock(JdbcTemplate.class);
        var rejected = assertThrows(IllegalStateException.class,
                () -> new AuditSignedMembershipImporter(
                        false, "", "", "synthetic-user-event-key",
                        auditJdbc, AUDIT_URL, "xerj_membership_writer",
                        WRITER_PASSWORD, AUDIT_URL, "xerj_audit_ingest"));
        assertEquals("Audit process cannot host membership reconciliation",
                rejected.getMessage());
    }

    @Test
    void normalListenerStartupWiresDisabledImporterWithoutAWriter() {
        JdbcTemplate auditJdbc = mock(JdbcTemplate.class);
        var component = new AuditSignedMembershipImporter(
                false, "", "", "synthetic-user-event-key",
                auditJdbc, "", "", "", AUDIT_URL, "xerj_audit_ingest");
        assertThrows(IllegalStateException.class,
                () -> component.importSigned("not-a-membership-token"));
        System.out.println(
                "XERJ_AUDIT_IMPORTER_PROCESS_BOUNDARY_FAIL_CLOSED_TEST_PASS");
    }
}
