#!/usr/bin/env bash
# S0-E6N: CI-only checksum-pinned PG16 migration REHEARSAL.
# This is NOT an application migration entrypoint or a production deploy tool.
set -euo pipefail
umask 077
fail() { echo "S0_E6N_MIGRATION_DENIED" >&2; exit 1; }
eq() { [[ "$1" == "$2" ]] || fail; }
eq "${GITHUB_ACTIONS:-}" true
eq "${AETHERIS_E6N_DISPOSABLE_MARKER:-}" ONLY_DISPOSABLE_E6N_PG16
eq "${AETHERIS_E6N_APPROVAL_SCOPE:-}" CI_ISOLATED_REVIEW_ONLY
eq "${PGHOST:-}" 127.0.0.1
eq "${PGPORT:-}" 5432
eq "${PGDATABASE:-}" aetheris_e6n_disposable
eq "${PGUSER:-}" e6n_ci_root
eq "${PGSSLMODE:-}" verify-full
test -r "${PGSSLROOTCERT:-/nonexistent}" || fail
command -v psql >/dev/null || fail
command -v git >/dev/null || fail
command -v sha256sum >/dev/null || fail
version="S0_E6N_001"
source_sql="identity-service/src/test/resources/security/e6j_runtime_session_ledger_pg16.sql"
source_blob="fcf0e61c24ab89b61f10b6d2e454742a4b0420b0"
test -f "$source_sql" || fail
eq "$(git hash-object "$source_sql")" "$source_blob"
test ! -d identity-service/src/main/resources/db/migration || fail
run() { psql -X -w -v ON_ERROR_STOP=1 -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" -d "$PGDATABASE" "$@"; }
eq "$(run -At -c 'SELECT current_database()')" aetheris_e6n_disposable
eq "$(run -At -c 'SELECT current_user')" e6n_ci_root
eq "$(run -At -c "SELECT current_setting('server_version_num')::integer BETWEEN 160000 AND 169999")" t
eq "$(run -At -c "SELECT ssl FROM pg_stat_ssl WHERE pid=pg_backend_pid()")" t
mode="${1:-}"
[[ "$mode" == --apply || "$mode" == --verify ]] || fail
sha="$(sha256sum "$source_sql" | cut -d' ' -f1)"
if [[ "$mode" == --apply ]]; then
    # A separate review journal is installed exclusively in this ephemeral CI
    # database, NOT in Identity src/main resources or production schema.
    run -q <<'SQL'
BEGIN;
CREATE SCHEMA IF NOT EXISTS e6n_review_meta;
CREATE TABLE IF NOT EXISTS e6n_review_meta.applied_migrations(
    version text PRIMARY KEY,
    git_blob_sha text NOT NULL CHECK(length(git_blob_sha)=40),
    sha256 text NOT NULL CHECK(length(sha256)=64),
    applied_at timestamptz NOT NULL DEFAULT now()
);
REVOKE ALL ON SCHEMA e6n_review_meta FROM PUBLIC;
REVOKE ALL ON e6n_review_meta.applied_migrations FROM PUBLIC;
COMMIT;
SQL
    # Missing journal means the review-only schema is not yet installed.
    eq "$(run -At -c "SELECT count(*) FROM e6n_review_meta.applied_migrations WHERE version='$version'")" 0
    eq "$(run -At -c "SELECT count(*) FROM pg_namespace WHERE nspname='e6j_runtime_auth'")" 0
    tmp="$(mktemp -d -t e6n-versioned-review-XXXXXXXX)"
    chmod 700 "$tmp"
    trap 'rm -rf -- "$tmp"' EXIT
    # The pinned E6J contract already has one top-level BEGIN and COMMIT.
    # Remove exactly those two delimiters and wrap DDL + journal insertion
    # in ONE psql -1 transaction, with a transaction-level advisory lock.
    eq "$(grep -cx '^BEGIN;$' "$source_sql")" 1
    eq "$(grep -cx '^COMMIT;$' "$source_sql")" 1
    {
      cat <<'SQL'
SELECT pg_advisory_xact_lock(616160001);
DO $e6n_guard$
BEGIN
  IF EXISTS(SELECT 1 FROM e6n_review_meta.applied_migrations
              WHERE version='S0_E6N_001') THEN
    RAISE EXCEPTION 'S0_E6N_DUPLICATE_MIGRATION_VERSION';
  END IF;
END
$e6n_guard$;
SQL
      sed '/^BEGIN;$/d; /^COMMIT;$/d' "$source_sql"
      cat <<'SQL'
INSERT INTO e6n_review_meta.applied_migrations(version,git_blob_sha,sha256)
VALUES('S0_E6N_001', :'source_blob', :'source_sha256');
SQL
    } > "$tmp/atomic.sql"
    # Never print the script or credentials; psql exits nonzero on any error.
    run -q --single-transaction -v source_blob="$source_blob" \
        -v source_sha256="$sha" -f "$tmp/atomic.sql"
    echo S0_E6N_ONE_TRANSACTION_VERSIONED_CONTRACT_AND_JOURNAL_PASS
fi
eq "$(run -At -c "SELECT git_blob_sha FROM e6n_review_meta.applied_migrations WHERE version='$version'")" "$source_blob"
eq "$(run -At -c "SELECT sha256 FROM e6n_review_meta.applied_migrations WHERE version='$version'")" "$sha"
eq "$(run -At -c "SELECT count(*) FROM e6n_review_meta.applied_migrations")" 1
eq "$(run -At -c "SELECT pg_get_userbyid(proowner) FROM pg_proc WHERE pronamespace='e6j_runtime_auth'::regnamespace AND proname='check_signed_session'")" e6j_exec_owner
eq "$(run -At -c "SELECT rolsuper FROM pg_roles WHERE rolname='e6j_exec_owner'")" f
eq "$(run -At -c "SELECT count(*) FROM pg_namespace WHERE nspname='e6j_runtime_auth'")" 1
echo S0_E6N_VERSIONED_MIGRATION_HASH_OWNER_AND_TLS_VERIFIED_PASS
