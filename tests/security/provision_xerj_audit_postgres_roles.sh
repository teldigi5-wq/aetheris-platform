#!/usr/bin/env bash
# Ephemeral postgres roles only; never run against a developer or production DB.
set -euo pipefail
: "${GITHUB_RUN_ID:?}"
: "${GITHUB_RUN_ATTEMPT:?}"
: "${XERJ_COMPOSE_PROJECT:?}"
: "${RUNNER_TEMP:?}"
: "${XERJ_AUDIT_INGEST_DB_PASSWORD:?}"
: "${XERJ_MEMBERSHIP_WRITER_DB_PASSWORD:?}"
test "${GITHUB_ACTIONS:-}" = true
[[ "$GITHUB_RUN_ID" =~ ^[0-9]+$ && "$GITHUB_RUN_ATTEMPT" =~ ^[0-9]+$ ]]
test "$XERJ_COMPOSE_PROJECT" = "xerj-mtls-ci-${GITHUB_RUN_ID}-${GITHUB_RUN_ATTEMPT}"
[[ "$RUNNER_TEMP" == /home/runner/* ]]
[[ "$XERJ_AUDIT_INGEST_DB_PASSWORD" =~ ^[0-9a-f]{64}$ ]]
[[ "$XERJ_MEMBERSHIP_WRITER_DB_PASSWORD" =~ ^[0-9a-f]{64}$ ]]
test "$XERJ_AUDIT_INGEST_DB_PASSWORD" != "$XERJ_MEMBERSHIP_WRITER_DB_PASSWORD"
test -s audit-service/src/main/resources/db/migration/V4__audit_membership_locked_admission.sql

# These roles exist only inside the unique CI postgres:16 container. The
# superuser is used to provision privilege boundaries and never passed to
# the actually launched Audit Spring application.
docker compose -p "$XERJ_COMPOSE_PROJECT" \
  -f tests/security/xerj-disposable-deps.compose.yml exec -T postgres \
  psql -X -q -v ON_ERROR_STOP=1 -U xerj_ci -d xerj_fence_audit \
  -v "ingest_password=$XERJ_AUDIT_INGEST_DB_PASSWORD" \
  -v "writer_password=$XERJ_MEMBERSHIP_WRITER_DB_PASSWORD" \
  > /dev/null <<'SQL'
CREATE ROLE xerj_audit_ingest LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT
    PASSWORD :'ingest_password';
CREATE ROLE xerj_membership_writer LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT
    PASSWORD :'writer_password';
GRANT CONNECT ON DATABASE xerj_fence_audit
    TO xerj_audit_ingest, xerj_membership_writer;
GRANT USAGE ON SCHEMA public
    TO xerj_audit_ingest, xerj_membership_writer;
-- Existing owner/superuser fixture retains access. PUBLIC and the other
-- application role do not acquire inherited table-level access.
REVOKE ALL ON TABLE public.audit_events FROM PUBLIC;
REVOKE ALL ON TABLE public.audit_user_tenant_membership_fence FROM PUBLIC;
REVOKE ALL ON FUNCTION public.audit_membership_can_ingest(
    UUID, BIGINT, TIMESTAMPTZ) FROM PUBLIC;
GRANT SELECT, INSERT ON TABLE public.audit_events TO xerj_audit_ingest;
GRANT EXECUTE ON FUNCTION public.audit_membership_can_ingest(
    UUID, BIGINT, TIMESTAMPTZ) TO xerj_audit_ingest;
GRANT SELECT, INSERT, UPDATE ON TABLE public.audit_user_tenant_membership_fence
    TO xerj_membership_writer;
-- Neither role receives schema CREATE, SUPERUSER, DB owner, membership DELETE
-- or GRANT OPTION. Do not grant Audit member-table UPDATE just for FOR SHARE.
SQL

echo XERJ_AUDIT_POSTGRES_DISTINCT_LEAST_PRIVILEGE_ROLES_PROVISIONED_PASS
