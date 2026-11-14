#!/usr/bin/env bash
# S0-E6M: NEVER a production migration runner. Exact CI-only rehearsal.
# PostgreSQL16 snapshot restore can resurrect revoked JWT sessions. Prove
# that an isolated, traffic-disabled restore fence permanently denies them.
set -euo pipefail
umask 077

fail() { printf '%s\n' "S0_E6M_DISPOSABLE_REHEARSAL_FAILED" >&2; exit 1; }
guard() { test "$1" = "$2" || fail; }

guard "$GITHUB_ACTIONS" true
guard "$AETHERIS_E6M_DISPOSABLE_MARKER" ONLY_DISPOSABLE_E6M_PG16
guard "$AETHERIS_E6M_TRAFFIC_DISABLED" CONFIRMED_FOR_CI_RESTORE
guard "$PGHOST" 127.0.0.1
guard "$PGPORT" 5432
guard "$PGDATABASE" aetheris_e6m_disposable
guard "$PGUSER" e6m_test_only
guard "$PGPASSWORD" e6m_disposable_ci_only

command -v pg_dump >/dev/null || fail
command -v pg_restore >/dev/null || fail
command -v psql >/dev/null || fail
command -v createdb >/dev/null || fail
command -v dropdb >/dev/null || fail
command -v openssl >/dev/null || fail
command -v python3 >/dev/null || fail

src="identity-service/src/test/resources/security/e6j_runtime_session_ledger_pg16.sql"
fence="docs/security/migrations/s0-e6m/restore_fence.sql"
test -f "$src" && test -f "$fence" || fail
guard "$(git hash-object "$src")" fcf0e61c24ab89b61f10b6d2e454742a4b0420b0
# No Flyway/Liquibase production migration is installed or triggered.
test ! -d identity-service/src/main/resources/db/migration || fail

admin() { psql -X -w -q -v ON_ERROR_STOP=1 -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" -d "$PGDATABASE" "$@"; }
restored() { psql -X -w -q -v ON_ERROR_STOP=1 -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" -d aetheris_e6m_restored "$@"; }
catalog() { psql -X -w -q -v ON_ERROR_STOP=1 -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" -d postgres "$@"; }
login_sql() {
  local username="$1" password="$2" database="$3" command="$4"
  PGPASSWORD="$password" psql -X -w -q -At -v ON_ERROR_STOP=1 \
    -h "$PGHOST" -p "$PGPORT" -U "$username" -d "$database" -c "$command"
}

guard "$(admin -At -c 'SELECT current_database()')" aetheris_e6m_disposable
guard "$(admin -At -c 'SELECT current_user')" e6m_test_only
guard "$(admin -At -c "SELECT current_setting('server_version_num')::integer BETWEEN 160000 AND 169999")" t
guard "$(admin -At -c "SELECT count(*) FROM pg_namespace WHERE nspname='e6j_runtime_auth'")" 0
guard "$(catalog -At -c "SELECT count(*) FROM pg_database WHERE datname='aetheris_e6m_restored'")" 0

tmp="$(mktemp -d -t e6m-review-XXXXXXXX)"
chmod 700 "$tmp"
trap 'rm -rf -- "$tmp"' EXIT

# A broken transactional DDL candidate must roll back *completely*.
admin -c "CREATE TABLE e6m_unrelated_record (id bigint PRIMARY KEY, marker text NOT NULL)"
admin -c "INSERT INTO e6m_unrelated_record VALUES (1,'OWNER_DATA_UNTOUCHED')"
if printf 'BEGIN;\nCREATE SCHEMA e6m_partial_must_rollback;\nSELECT 1/0;\nCOMMIT;\n' \
    | admin > /dev/null 2>&1; then
  fail
fi
guard "$(admin -At -c "SELECT count(*) FROM pg_namespace WHERE nspname='e6m_partial_must_rollback'")" 0
guard "$(admin -At -c "SELECT marker FROM e6m_unrelated_record WHERE id=1")" OWNER_DATA_UNTOUCHED
echo S0_E6M_FAILED_TRANSACTIONAL_DDL_ROLLBACK_PASS

# The only schema installation path is the exact verified S0-E6J reviewed
# disposable SQL. It is NOT placed in application resources/migration paths.
admin -f "$src"
guard "$(admin -At -c "SELECT count(*) FROM pg_namespace WHERE nspname='e6j_runtime_auth'")" 1
guard "$(admin -At -c "SELECT rolsuper FROM pg_roles WHERE rolname='e6j_exec_owner'")" f
guard "$(admin -At -c "SELECT pg_get_userbyid(proowner) FROM pg_proc WHERE pronamespace='e6j_runtime_auth'::regnamespace AND proname='check_signed_session'")" e6j_exec_owner

# Every CI login is a genuinely distinct network-authenticated, non-superuser
# PostgreSQL role. Random passwords exist only in this shell's environment.
reader_pass="$(openssl rand -hex 24)"
issuer_pass="$(openssl rand -hex 24)"
revoker_pass="$(openssl rand -hex 24)"
admin -v e6m_reader_pass="$reader_pass" \
      -v e6m_issuer_pass="$issuer_pass" \
      -v e6m_revoker_pass="$revoker_pass" <<'SQL'
CREATE ROLE e6m_reader_login LOGIN PASSWORD :'e6m_reader_pass'
  NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION;
CREATE ROLE e6m_issuer_login LOGIN PASSWORD :'e6m_issuer_pass'
  NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION;
CREATE ROLE e6m_revoker_login LOGIN PASSWORD :'e6m_revoker_pass'
  NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION;
GRANT e6j_reader TO e6m_reader_login;
GRANT e6j_issuer TO e6m_issuer_login;
GRANT e6j_revoker TO e6m_revoker_login;
SQL

guard "$(admin -At -c "SELECT has_function_privilege('e6m_reader_login','e6j_runtime_auth.check_signed_session(varchar,varchar,varchar,bigint,bigint)','EXECUTE')")" t
guard "$(admin -At -c "SELECT has_function_privilege('e6m_reader_login','e6j_runtime_auth.enroll_signed_session(varchar,varchar,varchar,bigint,bigint,bigint,uuid)','EXECUTE')")" f
guard "$(admin -At -c "SELECT has_function_privilege('e6m_reader_login','e6j_runtime_auth.revoke_signed_session(varchar,varchar,varchar,uuid)','EXECUTE')")" f
guard "$(admin -At -c "SELECT has_table_privilege('e6m_reader_login','e6j_runtime_auth.session_registrations','SELECT')")" f
guard "$(admin -At -c "SELECT has_table_privilege('e6m_reader_login','e6j_runtime_auth.session_registrations','UPDATE')")" f
guard "$(admin -At -c "SELECT has_table_privilege('e6m_issuer_login','e6j_runtime_auth.session_registrations','INSERT')")" f
guard "$(admin -At -c "SELECT has_table_privilege('e6m_revoker_login','e6j_runtime_auth.session_registrations','UPDATE')")" f
if login_sql e6m_reader_login "$reader_pass" "$PGDATABASE" \
    'SELECT * FROM e6j_runtime_auth.session_registrations' > /dev/null 2>&1; then
  fail
fi
if login_sql e6m_reader_login "$reader_pass" "$PGDATABASE" \
    "UPDATE e6j_runtime_auth.session_registrations SET state='REVOKED'" > /dev/null 2>&1; then
  fail
fi
echo S0_E6M_DISTINCT_NETWORK_LOGIN_AND_LEAST_PRIVILEGE_PASS

iss=https://identity.aetheris.example.invalid
kid=e6m_disposable_recovery
jti=e6m_review_session_uuid_4d67f12b9c3d
iat="$(($(date +%s)-2))"
exp="$((iat+180))"
admin -v iss="$iss" -v kid="$kid" <<'SQL'
INSERT INTO e6j_runtime_auth.signer_key_states (issuer,kid,state)
  VALUES (:'iss',:'kid','ACTIVE');
INSERT INTO e6j_runtime_auth.principal_epochs
  (principal_id,reject_issued_at_or_before_epoch) VALUES (42,0);
SQL
read_query="SELECT e6j_runtime_auth.check_signed_session('$iss','$kid','$jti',42,$iat)"
guard "$(login_sql e6m_reader_login "$reader_pass" "$PGDATABASE" "$read_query")" f
enroll_query="SELECT e6j_runtime_auth.enroll_signed_session('$iss','$kid','$jti',42,$iat,$exp,gen_random_uuid())"
guard "$(login_sql e6m_issuer_login "$issuer_pass" "$PGDATABASE" "$enroll_query")" t
guard "$(login_sql e6m_reader_login "$reader_pass" "$PGDATABASE" "$read_query")" t
guard "$(admin -At -c 'SELECT count(*) FROM e6j_runtime_auth.authority_audit')" 1
echo S0_E6M_NEW_SESSION_REGISTERED_ONLY_BY_ISOLATED_ISSUER_PASS

# A pre-revocation snapshot can resurrect tokens revoked after the backup.
# Never serve restored databases before applying a complete deny fence.
pg_dump -w -Fc -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" \
  -d "$PGDATABASE" -f "$tmp/point-in-time.custom"
test -s "$tmp/point-in-time.custom" || fail
chmod 600 "$tmp/point-in-time.custom"

revoke_query="SELECT e6j_runtime_auth.revoke_signed_session('$iss','$kid','$jti',gen_random_uuid())"
guard "$(login_sql e6m_revoker_login "$revoker_pass" "$PGDATABASE" "$revoke_query")" t
guard "$(login_sql e6m_reader_login "$reader_pass" "$PGDATABASE" "$read_query")" f
echo S0_E6M_SOURCE_REVOCATION_DENIED_PASS

createdb -w -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" aetheris_e6m_restored
pg_restore -w -e --single-transaction -h "$PGHOST" -p "$PGPORT" \
  -U "$PGUSER" -d aetheris_e6m_restored "$tmp/point-in-time.custom"
guard "$(restored -At -c "SELECT marker FROM e6m_unrelated_record WHERE id=1")" OWNER_DATA_UNTOUCHED
guard "$(restored -At -c "SELECT pg_get_userbyid(proowner) FROM pg_proc WHERE pronamespace='e6j_runtime_auth'::regnamespace AND proname='check_signed_session'")" e6j_exec_owner
guard "$(restored -At -c "SELECT has_table_privilege('e6m_reader_login','e6j_runtime_auth.session_registrations','SELECT')")" f
guard "$(restored -At -c "SELECT has_function_privilege('e6m_reader_login','e6j_runtime_auth.check_signed_session(varchar,varchar,varchar,bigint,bigint)','EXECUTE')")" t
# This TRUE proves restore WITHOUT a fence would wrongly resurrect a
# previously revoked JWT (negative security test, NEVER an allow decision).
guard "$(login_sql e6m_reader_login "$reader_pass" aetheris_e6m_restored "$read_query")" t
echo S0_E6M_UNFENCED_RESTORE_REPLAY_VULNERABILITY_REPRODUCED_PASS

# The workflow does not have a Runtime listener or ingress; traffic stays OFF.
# The recovery fence invalidates every restored signer and session together.
restored -f "$fence"
guard "$(login_sql e6m_reader_login "$reader_pass" aetheris_e6m_restored "$read_query")" f
guard "$(restored -At -c "SELECT count(*) FROM e6j_runtime_auth.session_registrations WHERE state='ACTIVE'")" 0
guard "$(restored -At -c "SELECT count(*) FROM e6j_runtime_auth.signer_key_states WHERE state='ACTIVE'")" 0
guard "$(restored -At -c "SELECT count(*) FROM e6j_runtime_auth.session_registrations WHERE state='REVOKED' AND revoked_at IS NOT NULL")" 1
guard "$(restored -At -c "SELECT count(*) FROM e6j_runtime_auth.authority_audit")" 3
restored -f "$fence"
guard "$(restored -At -c "SELECT count(*) FROM e6j_runtime_auth.authority_audit")" 3
guard "$(restored -At -c "SELECT marker FROM e6m_unrelated_record WHERE id=1")" OWNER_DATA_UNTOUCHED
echo S0_E6M_RESTORED_JTI_SIGNER_FENCE_IDEMPOTENT_AND_BASELINE_PRESERVED_PASS

# Review-only disposable cleanup. The runner also destroys this PG container.
dropdb -w -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" aetheris_e6m_restored
admin <<'SQL'
DROP SCHEMA e6j_runtime_auth CASCADE;
DROP ROLE e6m_reader_login;
DROP ROLE e6m_issuer_login;
DROP ROLE e6m_revoker_login;
DROP ROLE e6j_reader;
DROP ROLE e6j_issuer;
DROP ROLE e6j_revoker;
DROP ROLE e6j_outsider;
DROP ROLE e6j_exec_owner;
SQL
guard "$(admin -At -c "SELECT count(*) FROM pg_namespace WHERE nspname='e6j_runtime_auth'")" 0
guard "$(catalog -At -c "SELECT count(*) FROM pg_database WHERE datname='aetheris_e6m_restored'")" 0
guard "$(admin -At -c "SELECT marker FROM e6m_unrelated_record WHERE id=1")" OWNER_DATA_UNTOUCHED
echo S0_E6M_NO_PROD_MIGRATION_DISPOSABLE_BACKUP_RESTORE_AND_CLEANUP_PASS
