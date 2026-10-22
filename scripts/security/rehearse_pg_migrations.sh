#!/usr/bin/env bash
# Non-production synthetic rehearsal. Uses disposable Docker/PostgreSQL only.
set -euo pipefail
root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$root"
container="aetheris-schema-rehearsal-${GITHUB_RUN_ID:-local}-$$"
cleanup() { docker rm -f "$container" >/dev/null 2>&1 || true; }
trap cleanup EXIT
secret="$(python3 -c 'import secrets; print(secrets.token_hex(32))')"
if [[ -n "${GITHUB_ACTIONS:-}" ]]; then echo "::add-mask::$secret"; fi
docker run --detach --rm --name "$container" -e POSTGRES_PASSWORD="$secret" -p 127.0.0.1:55432:5432 postgres:16-alpine >/dev/null
# Keep the randomly generated test password in memory only; never log it.
test_password="$secret"
unset secret
for n in {1..45}; do
  if docker exec "$container" pg_isready -q -U postgres -d postgres; then break; fi
  sleep 1
done
docker exec "$container" pg_isready -q -U postgres -d postgres
db_psql() { docker exec -i "$container" psql -X -q -v ON_ERROR_STOP=1 -U postgres -d "$1" "${@:2}"; }
docker exec "$container" createdb -U postgres aetheris_fresh
db_psql aetheris_fresh < database/migration-candidates/V1__identity_schema.sql
db_psql aetheris_fresh < database/migration-candidates/V1__users_schema.sql
db_psql aetheris_fresh < audit-service/src/main/resources/db/migration/V1__create_audit_events.sql
count="$(db_psql aetheris_fresh -Atc "SELECT count(*) FROM information_schema.columns WHERE table_schema='public' AND table_name IN ('identity_accounts','identity_refresh_tokens','users','audit_events')")"
[[ "$count" == 21 ]] || { echo "Unexpected synthetic database column count: $count" >&2; exit 1; }
fk="$(db_psql aetheris_fresh -Atc "SELECT count(*) FROM pg_constraint WHERE conrelid = 'identity_refresh_tokens'::regclass AND contype = 'f'")"
[[ "$fk" == 1 ]] || { echo "Missing refresh token FK" >&2; exit 1; }
db_psql aetheris_fresh -c "INSERT INTO identity_accounts(name,email,password_hash,role) VALUES ('Synthetic Identity','fake@example.invalid','not-a-real-hash','API_CONSUMER')" >/dev/null
db_psql aetheris_fresh -c "INSERT INTO users(name,email) VALUES ('Synthetic User','fake@example.invalid')" >/dev/null
if db_psql aetheris_fresh -c "INSERT INTO users(name,email) VALUES ('Duplicate','fake@example.invalid')" >/dev/null 2>&1; then
  echo "Unique email invariant was not enforced" >&2; exit 1
fi
if db_psql aetheris_fresh -c "INSERT INTO identity_refresh_tokens(account_id,token_hash,expires_at,created_at) VALUES (987654,'placeholder',NOW(),NOW())" >/dev/null 2>&1; then
  echo "Orphaned refresh token insertion unexpectedly succeeded" >&2; exit 1
fi
before="$(db_psql aetheris_fresh -Atc "SELECT count(*) FROM users")"
[[ "$before" == 1 ]] || exit 1
# Unsafe rerun must fail rather than silently claim to baseline a populated DB.
if db_psql aetheris_fresh < database/migration-candidates/V1__users_schema.sql >/dev/null 2>&1; then
  echo "Unexpected migration success on a nonempty database" >&2; exit 1
fi
after="$(db_psql aetheris_fresh -Atc "SELECT count(*) FROM users")"
[[ "$after" == "$before" ]] || { echo "Rehearsal altered preexisting rows" >&2; exit 1; }
docker exec "$container" createdb -U postgres aetheris_legacy
db_psql aetheris_legacy -c "CREATE TABLE users (id BIGINT PRIMARY KEY, legacy_marker TEXT NOT NULL)" >/dev/null
db_psql aetheris_legacy -c "INSERT INTO users(id, legacy_marker) VALUES (1, 'preserve')" >/dev/null
if db_psql aetheris_legacy < database/migration-candidates/V1__users_schema.sql >/dev/null 2>&1; then
  echo "Unexpected migration success against unknown legacy schema" >&2; exit 1
fi
retained="$(db_psql aetheris_legacy -Atc "SELECT legacy_marker FROM users WHERE id=1")"
[[ "$retained" == "preserve" ]] || { echo "Legacy data was not preserved" >&2; exit 1; }
echo "PASS: fresh schema, FK/unique constraints, synthetic rows and legacy fail-closed checks"

# Run actual ORM validation against the freshly created PostgreSQL schema.
# This is an isolated GitHub Actions host; DB is bound to loopback only.
export SPRING_DATASOURCE_URL="jdbc:postgresql://127.0.0.1:55432/aetheris_fresh"
export SPRING_DATASOURCE_USERNAME=postgres
export SPRING_DATASOURCE_PASSWORD="$test_password"
export SPRING_DATASOURCE_DRIVER_CLASS_NAME=org.postgresql.Driver
export SPRING_JPA_HIBERNATE_DDL_AUTO=validate
unset test_password
./mvnw -B -ntp -pl identity-service -Daetheris.schema.rehearsal=true -Dtest=SchemaCandidateValidationTest test
./mvnw -B -ntp -pl user-service -Daetheris.schema.rehearsal=true -Dtest=SchemaCandidateValidationTest test
echo "PASS: Hibernate validated both service mappings against candidate PostgreSQL schema"
