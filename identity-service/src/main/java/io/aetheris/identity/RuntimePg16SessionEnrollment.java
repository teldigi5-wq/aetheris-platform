package io.aetheris.identity;

import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;
import java.util.UUID;

/**
 * S0-E6K: opt-in, function-only JDBC bridge to the S0-E6J disposable ledger.
 * REVIEW ONLY: no @Service, @Component, @Bean or production configuration.
 *
 * The caller MUST be a real Spring-managed @Transactional method on the
 * same PostgreSQL DataSource as refresh-token rotation. This is a prototype
 * using SET LOCAL ROLE on a privileged disposable CI account; it is NOT
 * a production issuer workload identity or permission to run on main.
 */
public final class RuntimePg16SessionEnrollment {
    private static final String CALL = """
        SELECT e6j_runtime_auth.enroll_signed_session(?,?,?,?,?,?,?)
        """;
    private final JdbcTemplate jdbc;

    public RuntimePg16SessionEnrollment(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "transaction-bound JDBC");
    }

    public void enroll(RuntimeAudienceTokenIssuer.VerifiedRegistration signed) {
        Objects.requireNonNull(signed, "verified signed issuer provenance");
        try {
            boolean accepted = Boolean.TRUE.equals(jdbc.execute(
                (ConnectionCallback<Boolean>) connection -> {
                    if (connection.getAutoCommit() || connection.isReadOnly()
                            || connection.getTransactionIsolation()
                                != Connection.TRANSACTION_READ_COMMITTED) {
                        throw new SQLException("No writable READ COMMITTED transaction");
                    }
                    try(Statement role = connection.createStatement()) {
                        role.execute("SET LOCAL ROLE e6j_issuer");
                    }
                    try {
                        try(PreparedStatement statement=connection.prepareStatement(CALL)) {
                            statement.setQueryTimeout(3);
                            statement.setString(1,signed.issuer());
                            statement.setString(2,signed.kid());
                            statement.setString(3,signed.jti());
                            statement.setLong(4,signed.uid());
                            statement.setLong(5,signed.issuedAtSeconds());
                            statement.setLong(6,signed.expiresAtSeconds());
                            statement.setObject(7,UUID.randomUUID());
                            try(var rows=statement.executeQuery()) {
                                if(!rows.next()) throw new SQLException("Missing registry result");
                                boolean answer=rows.getBoolean(1);
                                if(rows.next()) throw new SQLException("Ambiguous registry result");
                                return answer;
                            }
                        }
                    } finally {
                        // Do not leak the issuer role into subsequent JPA work
                        // on the transaction-bound pooled connection.
                        try(Statement reset = connection.createStatement()) {
                            reset.execute("RESET ROLE");
                        }
                    }
                }));
            if (!accepted) throw new SessionEnrollmentRejectedException();
        } catch (RuntimeException failed) {
            // Hibernate/JDBC state is rolled back by the outer Spring proxy.
            // Do not log a JWT, SQL statement, principal or credential.
            throw new SessionEnrollmentRejectedException();
        }
    }

    public static final class SessionEnrollmentRejectedException
            extends RuntimeException {
        public SessionEnrollmentRejectedException() {
            super("Runtime session enrollment denied");
        }
    }
}
