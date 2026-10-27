#!/usr/bin/env bash
# Single disposable PostgreSQL 16 physical DB / two independent schemas.
# NO real data, existing volumes, migration baselines or production privileges.
set -euo pipefail
[[ "${AETHERIS_SHARED_SCHEMA_SYNTHETIC_ONLY:-}" == "yes" ]] || {
  echo SHARED_SCHEMA_CI_ONLY_OPT_IN_REQUIRED
  exit 2
}
cd "$(dirname "$0")/../.."
container="aetheris-shared-schema-${GITHUB_RUN_ID:-local}-$$"
cleanup() { docker rm -f "$container" >/dev/null 2>&1 || true; }
trap cleanup EXIT
pw="$(python3 -c 'import secrets; print(secrets.token_hex(32))')"
if [[ -n "${GITHUB_ACTIONS:-}" ]]; then echo "::add-mask::$pw"; fi
docker run -d --rm --name "$container" -e "POSTGRES_PASSWORD=$pw" \
  -p 127.0.0.1:55435:5432 postgres:16-alpine >/dev/null
ready=0
for i in $(seq 1 80); do
  if docker exec "$container" pg_isready -q -U postgres -d postgres -h 127.0.0.1 &&
     docker exec "$container" createdb -U postgres shared_synthetic; then
    ready=1; break
  fi
  sleep 0.5
done
[[ "$ready" == 1 ]] || { echo SHARED_SCHEMA_DB_NOT_READY; exit 1; }
sql() {
  docker exec "$container" psql -X -qAt -v ON_ERROR_STOP=1 \
    -U postgres -d shared_synthetic -c "$1"
}
sql "CREATE SCHEMA identity_stage AUTHORIZATION postgres;" >/dev/null
sql "CREATE SCHEMA user_stage AUTHORIZATION postgres;" >/dev/null
sql "CREATE SCHEMA audit_stage AUTHORIZATION postgres;" >/dev/null
export SPRING_DATASOURCE_USERNAME=postgres
export SPRING_DATASOURCE_PASSWORD="$pw"
export SPRING_DATASOURCE_DRIVER_CLASS_NAME=org.postgresql.Driver
export AETHERIS_FLYWAY_MIGRATION_ENABLED=true
export SPRING_JPA_HIBERNATE_DDL_AUTO=validate
unset pw
run_scoped() {
  local service="$1" namespace="$2"
  export SPRING_FLYWAY_SCHEMAS="$namespace"
  export SPRING_FLYWAY_DEFAULT_SCHEMA="$namespace"
  export SPRING_DATASOURCE_URL="jdbc:postgresql://127.0.0.1:55435/shared_synthetic?currentSchema=$namespace"
  ./mvnw -B -ntp -pl "$service" \
    -Daetheris.flyway.shared.synthetic=true \
    -Dtest=SharedDatabaseSchemaIsolationTest test
}
run_scoped identity-service identity_stage
run_scoped user-service user_stage
echo SHARED_SCHEMA_IDENTITY_AND_USER_FLYWAY_FIRST_START_SUCCESS
# Both same-version V1 migrations must be distinct, stored under their
# individual schema histories, with no objects in public schema.
for namespace in identity_stage user_stage; do
  [[ "$(sql "SELECT count(*) FROM $namespace.flyway_schema_history WHERE version='1' AND success;")" == 1 ]] || {
    echo SHARED_SCHEMA_MISSING_VERSIONED_HISTORY; exit 1;
  }
  [[ "$(sql "SELECT count(*) FROM $namespace.flyway_schema_history;")" == 1 ]] || {
    echo SHARED_SCHEMA_EXTRA_VERSIONED_HISTORY; exit 1;
  }
done
[[ "$(sql "SELECT count(*) FROM information_schema.tables
WHERE table_schema='public' AND table_name IN
('identity_accounts','identity_refresh_tokens','users','flyway_schema_history');")" == 0 ]] || {
  echo SHARED_SCHEMA_PUBLIC_COLLISION; exit 1;
}
[[ "$(sql "SELECT count(*) FROM information_schema.tables WHERE table_schema='identity_stage' AND table_name='users';")" == 0 ]] || exit 1
[[ "$(sql "SELECT count(*) FROM information_schema.tables WHERE table_schema='user_stage' AND table_name='identity_accounts';")" == 0 ]] || exit 1

# Insert only synthetic sentinel data to verify isolation and restart.
sql "INSERT INTO identity_stage.identity_accounts(name,email,password_hash,role)
VALUES ('Synthetic Identity','fake-identity@example.invalid','not-real','API_CONSUMER');" >/dev/null
sql "INSERT INTO user_stage.users(name,email)
VALUES ('Synthetic User','fake-user@example.invalid');" >/dev/null
run_scoped user-service user_stage
run_scoped identity-service identity_stage
[[ "$(sql "SELECT count(*) FROM identity_stage.identity_accounts
WHERE email='fake-identity@example.invalid';")" == 1 ]] || exit 1
[[ "$(sql "SELECT count(*) FROM user_stage.users
WHERE email='fake-user@example.invalid';")" == 1 ]] || exit 1
for namespace in identity_stage user_stage; do
  [[ "$(sql "SELECT count(*) FROM $namespace.flyway_schema_history;")" == 1 ]] || exit 1
done
echo SHARED_SCHEMA_RESTARTS_NO_DUPLICATE_MIGRATIONS_AND_ROWS_PRESERVED

# This is a privileged-CI topology, not production least-privilege roles.
# Audit has its own existing Flyway history and needs independent rehearsal.
[[ "$(sql "SELECT count(*) FROM information_schema.tables
WHERE table_schema='audit_stage';")" == 0 ]] || {
  echo SHARED_SCHEMA_UNREVIEWED_AUDIT_ACTIVATION; exit 1;
}
echo SHARED_SCHEMA_AUDIT_NOT_MIGRATED_NEEDS_INDEPENDENT_REVIEW
echo SHARED_SCHEMA_TEST_ONLY_NO_PRODUCTION_ACTIVATION_NO_GO
