-- B62-G8: CI-only isolated PostgreSQL 16 credentials. NEVER use in prod.
-- All passwords are intentionally synthetic, fixed, non-deployable test data.
-- Depends on B62-G1 schema and B62-G2 NOLOGIN verifier group role.
BEGIN;
CREATE ROLE b62g8_gateway_reader LOGIN INHERIT NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION PASSWORD 'b62g8-gateway-synthetic-ci-only';
CREATE ROLE b62g8_user_reader LOGIN INHERIT NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION PASSWORD 'b62g8-user-synthetic-ci-only';
CREATE ROLE b62g8_audit_reader LOGIN INHERIT NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION PASSWORD 'b62g8-audit-synthetic-ci-only';
CREATE ROLE b62g8_emergency_writer LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION PASSWORD 'b62g8-writer-synthetic-ci-only';

GRANT b62g_verifier TO b62g8_gateway_reader, b62g8_user_reader, b62g8_audit_reader;
GRANT CONNECT ON DATABASE b62g_synthetic TO b62g8_gateway_reader, b62g8_user_reader, b62g8_audit_reader, b62g8_emergency_writer;

-- The writer is not a table owner / DDL admin. Narrow grants support only
-- the existing SECURITY INVOKER functions, not DELETE or role administration.
GRANT USAGE ON SCHEMA b62g_proof TO b62g8_emergency_writer;
GRANT SELECT, INSERT, UPDATE ON b62g_proof.principal_epochs TO b62g8_emergency_writer;
GRANT SELECT, INSERT ON b62g_proof.blocked_signer_kids TO b62g8_emergency_writer;
GRANT EXECUTE ON FUNCTION b62g_proof.raise_principal_epoch(bigint,bigint)
    TO b62g8_emergency_writer;
GRANT EXECUTE ON FUNCTION b62g_proof.block_signer(text,text,text)
    TO b62g8_emergency_writer;

-- Revoke defaults for public before checking the real logins.
REVOKE ALL ON ALL TABLES IN SCHEMA b62g_proof FROM PUBLIC;
REVOKE ALL ON ALL FUNCTIONS IN SCHEMA b62g_proof FROM PUBLIC;
COMMIT;

-- For clarity: role membership is not privileged PostgreSQL superuser mode.
SELECT 'B62G8_AUTHENTICATED_SYNTHETIC_ROLES_INSTALLED';
