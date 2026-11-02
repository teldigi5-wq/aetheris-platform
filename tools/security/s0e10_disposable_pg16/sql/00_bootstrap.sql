\set ON_ERROR_STOP on
-- S0-E.7: NEVER RUN AGAINST A HISTORICAL/PRODUCTION DATABASE.
-- Must execute only in disposable PG16 cluster/database named s0e7_disposable.
BEGIN;
DO $guard$
BEGIN
  IF current_database() <> 's0e7_disposable' OR
     current_setting('server_version_num')::int / 10000 <> 16 OR
     current_setting('is_superuser') <> 'on' OR
     EXISTS (SELECT 1 FROM pg_namespace WHERE nspname NOT IN ('public','information_schema')
               AND nspname NOT LIKE 'pg_%') OR
     to_regclass('public.users') IS NOT NULL OR
     to_regclass('public.audit_events') IS NOT NULL THEN
    RAISE EXCEPTION 'S0E7_REJECT_NONFRESH_DISPOSABLE_PG16';
  END IF;
END
$guard$;

-- Bootstrap credentials are never assigned to application roles.
REVOKE ALL ON DATABASE s0e7_disposable FROM PUBLIC;
REVOKE CREATE ON SCHEMA public FROM PUBLIC;

CREATE ROLE s0e7_identity_owner NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS;
CREATE ROLE s0e7_user_owner NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS;
CREATE ROLE s0e7_audit_owner NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS;
CREATE ROLE s0e7_orchestrator_owner NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS;
CREATE ROLE s0e7_identity_runtime NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS;
CREATE ROLE s0e7_user_runtime NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS;
CREATE ROLE s0e7_audit_runtime NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS;
CREATE ROLE s0e7_orchestrator_runtime NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS;

CREATE SCHEMA s0e7_identity AUTHORIZATION s0e7_identity_owner;
CREATE SCHEMA s0e7_user AUTHORIZATION s0e7_user_owner;
CREATE SCHEMA s0e7_audit AUTHORIZATION s0e7_audit_owner;
CREATE SCHEMA s0e7_orchestrator AUTHORIZATION s0e7_orchestrator_owner;

REVOKE ALL ON SCHEMA s0e7_identity, s0e7_user, s0e7_audit, s0e7_orchestrator FROM PUBLIC;
GRANT USAGE ON SCHEMA s0e7_identity TO s0e7_identity_runtime;
GRANT USAGE ON SCHEMA s0e7_user TO s0e7_user_runtime;
GRANT USAGE ON SCHEMA s0e7_audit TO s0e7_audit_runtime;
GRANT USAGE ON SCHEMA s0e7_orchestrator TO s0e7_orchestrator_runtime;

-- Future tables remain inaccessible until an explicit reviewed grant.
-- No role memberships, login passwords, or CREATE privileges are granted.
COMMIT;
