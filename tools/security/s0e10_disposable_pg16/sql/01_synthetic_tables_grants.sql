\set ON_ERROR_STOP on
-- Synthetic, non-PII fixtures in a disposable cluster only. No production migration.
BEGIN;
DO $guard$
BEGIN
  IF current_database() <> 's0e7_disposable' OR
     current_setting('server_version_num')::int / 10000 <> 16 OR
     current_setting('is_superuser') <> 'on' OR
     to_regnamespace('s0e7_user') IS NULL OR
     to_regnamespace('s0e7_identity') IS NULL OR
     to_regnamespace('s0e7_audit') IS NULL OR
     to_regnamespace('s0e7_orchestrator') IS NULL THEN
    RAISE EXCEPTION 'S0E7_REJECT_UNEXPECTED_DATABASE_STATE';
  END IF;
END
$guard$;

CREATE TABLE s0e7_identity.records(id bigint PRIMARY KEY, synthetic_marker text NOT NULL);
CREATE TABLE s0e7_user.records(id bigint PRIMARY KEY, synthetic_marker text NOT NULL);
CREATE TABLE s0e7_audit.records(id bigint PRIMARY KEY, synthetic_marker text NOT NULL);
CREATE TABLE s0e7_orchestrator.records(id bigint PRIMARY KEY, synthetic_marker text NOT NULL);

ALTER TABLE s0e7_identity.records OWNER TO s0e7_identity_owner;
ALTER TABLE s0e7_user.records OWNER TO s0e7_user_owner;
ALTER TABLE s0e7_audit.records OWNER TO s0e7_audit_owner;
ALTER TABLE s0e7_orchestrator.records OWNER TO s0e7_orchestrator_owner;

REVOKE ALL ON ALL TABLES IN SCHEMA s0e7_identity, s0e7_user, s0e7_audit, s0e7_orchestrator FROM PUBLIC;
GRANT SELECT, INSERT, UPDATE, DELETE ON s0e7_identity.records TO s0e7_identity_runtime;
GRANT SELECT, INSERT, UPDATE, DELETE ON s0e7_user.records TO s0e7_user_runtime;
GRANT SELECT, INSERT ON s0e7_audit.records TO s0e7_audit_runtime;
GRANT SELECT, INSERT, UPDATE ON s0e7_orchestrator.records TO s0e7_orchestrator_runtime;

-- Explicit grants only. Runtime cannot change schemas, grant tables, or create roles.
INSERT INTO s0e7_identity.records VALUES (1, 'SYNTHETIC_IDENTITY_ONLY');
INSERT INTO s0e7_user.records VALUES (1, 'SYNTHETIC_USER_ONLY');
INSERT INTO s0e7_audit.records VALUES (1, 'SYNTHETIC_AUDIT_ONLY');
INSERT INTO s0e7_orchestrator.records VALUES (1, 'SYNTHETIC_JOB_ONLY');
COMMIT;
