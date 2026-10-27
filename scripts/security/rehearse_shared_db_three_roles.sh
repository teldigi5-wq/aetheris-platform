#!/usr/bin/env bash
# Synthetic-only PostgreSQL 16: independent Flyway identities and restricted
# runtime logins for Identity, User and Audit in ONE physical database.
# Never point this script at existing volumes or a user-supplied DB.
set -euo pipefail
[[ "$AETHERIS_THREE_ROLE_SYNTHETIC_ONLY" == "yes" ]] || {
  echo THREE_ROLE_EXPLICIT_SYNTHETIC_OPT_IN_REQUIRED; exit 2;
}
cd "$(dirname "$0")/../.."
container="aetheris-three-roles-$$"
cleanup() { docker rm -f "$container" >/dev/null 2>&1 || true; }
trap cleanup EXIT
secret() { python3 -c 'import secrets; print(secrets.token_hex(32))'; }
superpass="$(secret)"
idmp="$(secret)"; usmp="$(secret)"; aump="$(secret)"
idrp="$(secret)"; usrp="$(secret)"; aurp="$(secret)"
if [[ -n "$GITHUB_ACTIONS" ]]; then
  for v in "$superpass" "$idmp" "$usmp" "$aump" "$idrp" "$usrp" "$aurp"; do
    echo "::add-mask::$v"
  done
fi
docker run -d --rm --name "$container" -e "POSTGRES_PASSWORD=$superpass" \
  -p 127.0.0.1:55436:5432 postgres:16-alpine >/dev/null
ready=0
for i in $(seq 1 80); do
  if docker exec "$container" pg_isready -q -U postgres -d postgres -h 127.0.0.1 &&
     docker exec "$container" createdb -U postgres shared_synthetic; then
    ready=1; break
  fi
  sleep 0.5
done
[[ "$ready" == 1 ]] || { echo THREE_ROLE_POSTGRES_NOT_READY; exit 1; }
admin() {
  docker exec "$container" psql -X -qAt -v ON_ERROR_STOP=1 -U postgres \
    -d shared_synthetic -c "$1"
}
# No PUBLIC schema/database grants and no superuser application credentials.
admin "REVOKE ALL ON DATABASE shared_synthetic FROM PUBLIC;"
admin "REVOKE ALL ON SCHEMA public FROM PUBLIC;"
admin "CREATE ROLE identity_migrator LOGIN PASSWORD '$idmp';"
admin "CREATE ROLE user_migrator LOGIN PASSWORD '$usmp';"
admin "CREATE ROLE audit_migrator LOGIN PASSWORD '$aump';"
admin "CREATE ROLE identity_runtime LOGIN PASSWORD '$idrp';"
admin "CREATE ROLE user_runtime LOGIN PASSWORD '$usrp';"
admin "CREATE ROLE audit_runtime LOGIN PASSWORD '$aurp';"
admin "GRANT CONNECT ON DATABASE shared_synthetic TO identity_migrator, user_migrator, audit_migrator, identity_runtime, user_runtime, audit_runtime;"
admin "CREATE SCHEMA identity_stage AUTHORIZATION identity_migrator;"
admin "CREATE SCHEMA user_stage AUTHORIZATION user_migrator;"
admin "CREATE SCHEMA audit_stage AUTHORIZATION audit_migrator;"
admin "REVOKE ALL ON SCHEMA identity_stage, user_stage, audit_stage FROM PUBLIC;"
admin "GRANT USAGE ON SCHEMA identity_stage TO identity_runtime;"
admin "GRANT USAGE ON SCHEMA user_stage TO user_runtime;"
admin "GRANT USAGE ON SCHEMA audit_stage TO audit_runtime;"

run_migration() {
  local login="$1" pass="$2" schema="$3" module="$4" testname="$5" flag="$6"
  SPRING_DATASOURCE_USERNAME="$login" \
  SPRING_DATASOURCE_PASSWORD="$pass" \
  SPRING_DATASOURCE_DRIVER_CLASS_NAME=org.postgresql.Driver \
  SPRING_DATASOURCE_URL="jdbc:postgresql://127.0.0.1:55436/shared_synthetic?currentSchema=$schema" \
  SPRING_FLYWAY_SCHEMAS="$schema" \
  SPRING_FLYWAY_DEFAULT_SCHEMA="$schema" \
  AETHERIS_FLYWAY_MIGRATION_ENABLED=true \
  SPRING_JPA_HIBERNATE_DDL_AUTO=validate \
    ./mvnw -B -ntp -pl "$module" "-D$flag=true" "-Dtest=$testname" test
}
run_all_migrations() {
  run_migration identity_migrator "$idmp" identity_stage identity-service SharedDatabaseSchemaIsolationTest aetheris.flyway.shared.synthetic
  run_migration user_migrator "$usmp" user_stage user-service SharedDatabaseSchemaIsolationTest aetheris.flyway.shared.synthetic
  run_migration audit_migrator "$aump" audit_stage audit-service SharedDatabaseAuditSchemaIsolationTest aetheris.audit.shared.synthetic
}
run_all_migrations
echo THREE_ROLE_ACTUAL_FLYWAY_AND_HIBERNATE_FIRST_START_SUCCESS

# Runtime accounts get ONLY the DML needed by each service. No rights to
# any Flyway history table, foreign schema, schema CREATE, or schema OWNER.
admin "GRANT SELECT, INSERT, UPDATE, DELETE ON identity_stage.identity_accounts, identity_stage.identity_refresh_tokens TO identity_runtime;"
admin "GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA identity_stage TO identity_runtime;"
admin "GRANT SELECT, INSERT, UPDATE, DELETE ON user_stage.users TO user_runtime;"
admin "GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA user_stage TO user_runtime;"
admin "GRANT SELECT, INSERT ON audit_stage.audit_events TO audit_runtime;"
role_sql() {
  local login="$1" pass="$2" statement="$3"
  docker exec -e "PGPASSWORD=$pass" "$container" psql -X -qAt \
    -v ON_ERROR_STOP=1 -h 127.0.0.1 -U "$login" -d shared_synthetic -c "$statement"
}
assert_denied() {
  local output
  if output="$(role_sql "$1" "$2" "$3" 2>&1)"; then
    echo THREE_ROLE_UNEXPECTED_PRIVILEGE; exit 1
  fi
  case "$output" in
    *"permission denied"*|*"must be owner"*) ;;
    *) echo THREE_ROLE_UNEXPECTED_ERROR_NOT_PERMISSION_DENIAL; exit 1;;
  esac
}
for login in identity_migrator user_migrator audit_migrator identity_runtime user_runtime audit_runtime; do
  case "$login" in
    identity_migrator) pass="$idmp";;
    user_migrator) pass="$usmp";;
    audit_migrator) pass="$aump";;
    identity_runtime) pass="$idrp";;
    user_runtime) pass="$usrp";;
    audit_runtime) pass="$aurp";;
  esac
  [[ "$(role_sql "$login" "$pass" 'SELECT 1')" == "1" ]] || exit 1
done
echo THREE_ROLE_SIX_AUTHENTICATED_NON_SUPERUSER_LOGINS_SUCCESS

role_sql identity_runtime "$idrp" "INSERT INTO identity_stage.identity_accounts(name,email,password_hash,role) VALUES ('Synthetic','identity-role@example.invalid','not-real','API_CONSUMER');" >/dev/null
role_sql user_runtime "$usrp" "INSERT INTO user_stage.users(name,email) VALUES ('Synthetic','user-role@example.invalid');" >/dev/null
role_sql audit_runtime "$aurp" "INSERT INTO audit_stage.audit_events(event_id,event_type,occurred_at,user_id,name,email) VALUES ('evt-synthetic-role','USER_CREATED',now(),1,'Synthetic','audit-role@example.invalid');" >/dev/null
[[ "$(role_sql identity_runtime "$idrp" "SELECT count(*) FROM identity_stage.identity_accounts WHERE email='identity-role@example.invalid';")" == "1" ]] || exit 1
[[ "$(role_sql user_runtime "$usrp" "SELECT count(*) FROM user_stage.users WHERE email='user-role@example.invalid';")" == "1" ]] || exit 1
[[ "$(role_sql audit_runtime "$aurp" "SELECT count(*) FROM audit_stage.audit_events WHERE event_id='evt-synthetic-role';")" == "1" ]] || exit 1
assert_denied identity_runtime "$idrp" "SELECT count(*) FROM user_stage.users;"
assert_denied user_runtime "$usrp" "SELECT count(*) FROM audit_stage.audit_events;"
assert_denied audit_runtime "$aurp" "SELECT count(*) FROM identity_stage.identity_accounts;"
assert_denied identity_migrator "$idmp" "CREATE TABLE user_stage.cross_schema_ddl(id int);"
assert_denied user_migrator "$usmp" "DELETE FROM identity_stage.flyway_schema_history;"
assert_denied audit_migrator "$aump" "DELETE FROM user_stage.users;"
assert_denied identity_runtime "$idrp" "DELETE FROM identity_stage.flyway_schema_history;"
assert_denied user_runtime "$usrp" "UPDATE user_stage.flyway_schema_history SET version='2';"
assert_denied audit_runtime "$aurp" "SELECT count(*) FROM audit_stage.flyway_audit_schema_history;"
assert_denied audit_runtime "$aurp" "DELETE FROM audit_stage.audit_events;"
assert_denied user_runtime "$usrp" "CREATE TABLE user_stage.runtime_ddl(id int);"
assert_denied audit_runtime "$aurp" "CREATE TABLE public.runtime_ddl(id int);"
echo THREE_ROLE_CROSS_SCHEMA_MIGRATION_AND_AUDIT_MUTATION_DENIED

# Repeat real Flyway startup, preserve all synthetic DML and histories.
run_all_migrations
for history in identity_stage.flyway_schema_history user_stage.flyway_schema_history audit_stage.flyway_audit_schema_history; do
  [[ "$(admin "SELECT count(*) FROM $history WHERE version='1' AND success;")" == 1 ]] || exit 1
  [[ "$(admin "SELECT count(*) FROM $history;")" == 1 ]] || exit 1
done
[[ "$(admin "SELECT count(*) FROM identity_stage.identity_accounts WHERE email='identity-role@example.invalid';")" == 1 ]] || exit 1
[[ "$(admin "SELECT count(*) FROM user_stage.users WHERE email='user-role@example.invalid';")" == 1 ]] || exit 1
[[ "$(admin "SELECT count(*) FROM audit_stage.audit_events WHERE event_id='evt-synthetic-role';")" == 1 ]] || exit 1
[[ "$(admin "SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_name IN ('audit_events','users','identity_accounts','flyway_schema_history','flyway_audit_schema_history');")" == 0 ]] || exit 1
echo THREE_ROLE_SECOND_START_HISTORY_AND_SYNTHETIC_ROWS_PRESERVED

# Backup and restore ONLY this disposable synthetic database.
docker exec "$container" pg_dump -U postgres -Fc -d shared_synthetic -f /tmp/three-role-synthetic.dump
docker exec "$container" createdb -U postgres synthetic_restored
docker exec "$container" pg_restore --exit-on-error -U postgres -d synthetic_restored /tmp/three-role-synthetic.dump
[[ "$(docker exec "$container" psql -X -qAt -U postgres -d synthetic_restored -c "SELECT count(*) FROM audit_stage.audit_events WHERE event_id='evt-synthetic-role';")" == 1 ]] || exit 1
echo THREE_ROLE_SYNTHETIC_BACKUP_RESTORE_SUCCESS
echo THREE_ROLE_REVIEW_ONLY_NO_HISTORICAL_MIGRATION_STAGE45_NO_GO
