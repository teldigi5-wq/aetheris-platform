#!/usr/bin/env bash
# Synthetic opt-in Flyway proof against disposable localhost PostgreSQL only.
set -euo pipefail
[[ "${AETHERIS_FLYWAY_SYNTHETIC_CI_ONLY:-}" == "yes" ]] || {
  echo FLYWAY_REHEARSAL_REQUIRES_SYNTHETIC_OPT_IN
  exit 2
}
cd "$(dirname "$0")/../.."
container="aetheris-flyway-ci-${GITHUB_RUN_ID:-local}-$$"
log="$(mktemp)"
cleanup() {
  docker rm -f "$container" >/dev/null 2>&1 || true
  rm -f "$log"
}
trap cleanup EXIT
secret="$(python3 -c 'import secrets; print(secrets.token_hex(32))')"
if [[ -n "${GITHUB_ACTIONS:-}" ]]; then echo "::add-mask::$secret"; fi
docker run -d --rm --name "$container" -e "POSTGRES_PASSWORD=$secret" \
  -p 127.0.0.1:55433:5432 postgres:16-alpine >/dev/null
db_sql() {
  docker exec "$container" psql -X -qAt -v ON_ERROR_STOP=1 -U postgres -d "$1" -c "$2"
}
ready=0
for n in $(seq 1 80); do
  if docker exec "$container" pg_isready -q -h 127.0.0.1 -p 5432 -U postgres -d postgres &&
    docker exec "$container" createdb -U postgres flyway_identity; then
    ready=1
    break
  fi
  sleep 0.5
done
[[ "$ready" == 1 ]] || { echo FLYWAY_DISPOSABLE_DATABASE_NOT_READY; exit 1; }
docker exec "$container" createdb -U postgres flyway_user
docker exec "$container" createdb -U postgres flyway_unknown_legacy
db_sql flyway_unknown_legacy \
  "CREATE TABLE identity_accounts(id bigint PRIMARY KEY, legacy_marker text NOT NULL);" >/dev/null
db_sql flyway_unknown_legacy \
  "INSERT INTO identity_accounts(id,legacy_marker) VALUES (7,'retain-unknown-data');" >/dev/null
export SPRING_DATASOURCE_USERNAME=postgres
export SPRING_DATASOURCE_PASSWORD="$secret"
export SPRING_DATASOURCE_DRIVER_CLASS_NAME=org.postgresql.Driver
export AETHERIS_FLYWAY_MIGRATION_ENABLED=true
export SPRING_JPA_HIBERNATE_DDL_AUTO=validate
unset secret
run_maven() {
  local svc="$1" db="$2"
  export SPRING_DATASOURCE_URL="jdbc:postgresql://127.0.0.1:55433/$db"
  ./mvnw -B -ntp -pl "$svc" \
    -Daetheris.flyway.synthetic=true -Dtest=FlywayOptInFreshDatabaseTest test
}
run_maven identity-service flyway_identity
run_maven user-service flyway_user
for db in flyway_identity flyway_user; do
  applied="$(db_sql "$db" "SELECT count(*) FROM flyway_schema_history WHERE version='1' AND success;")"
  [[ "$applied" == 1 ]] || { echo FLYWAY_HISTORY_NOT_APPLIED_ONCE; exit 1; }
done
echo FLYWAY_REAL_CLEAN_DATABASE_IDENTITY_AND_USER_MIGRATIONS_PASS
run_maven identity-service flyway_identity
run_maven user-service flyway_user
for db in flyway_identity flyway_user; do
  count="$(db_sql "$db" "SELECT count(*) FROM flyway_schema_history;")"
  [[ "$count" == 1 ]] || { echo FLYWAY_REPLAY_CREATED_EXTRA_HISTORY; exit 1; }
done
echo FLYWAY_REPEATED_STARTUP_IDEMPOTENT
# A nonzero test exit alone cannot count as proof of legacy protection;
# require Flyway's explicit nonempty-schema refusal and untouched sentinel.
export SPRING_DATASOURCE_URL="jdbc:postgresql://127.0.0.1:55433/flyway_unknown_legacy"
if ./mvnw -B -ntp -pl identity-service \
    -Daetheris.flyway.synthetic=true -Dtest=FlywayOptInFreshDatabaseTest test \
    >"$log" 2>&1; then
  echo FLYWAY_UNSAFE_LEGACY_AUTOMIGRATION_ACCEPTED
  exit 1
fi
if ! grep -Eqi 'non-empty schema|non.empty schema|baselineOnMigrate' "$log"; then
  echo FLYWAY_UNKNOWN_LEGACY_FAILED_FOR_UNEXPECTED_REASON
  exit 1
fi
legacy="$(db_sql flyway_unknown_legacy \
  "SELECT legacy_marker FROM identity_accounts WHERE id=7;")"
[[ "$legacy" == "retain-unknown-data" ]] || {
  echo FLYWAY_UNKNOWN_LEGACY_DATA_MODIFIED
  exit 1
}
missing="$(db_sql flyway_unknown_legacy \
  "SELECT to_regclass('public.flyway_schema_history') IS NULL;")"
[[ "$missing" == t ]] || { echo FLYWAY_UNAUTHORIZED_LEGACY_BASELINE_CREATED; exit 1; }
echo FLYWAY_REAL_UNKNOWN_LEGACY_SCHEMA_REJECTED_UNCHANGED
echo FLYWAY_TEST_ONLY_OWNER_APPROVAL_STILL_REQUIRED_NO_GO
