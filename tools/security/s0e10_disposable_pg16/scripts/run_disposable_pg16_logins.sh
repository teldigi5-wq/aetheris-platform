#!/usr/bin/env bash
# S0-E.8 OPTIONAL authenticated-login proof on a NEW, isolated scratch PG16.
# No existing Compose services, volumes, credentials or network attachments.
# NOT invoked by offline verification. Run only after explicit owner approval.
set -euo pipefail
cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.."

if [[ "${S0E8_EXPLICIT_DISPOSABLE_APPROVAL:-}" != 'YES_FRESH_PG16_LOGIN_TEST' ]]; then
  echo 'S0-E.8 DENIED: explicit disposable database approval absent; nothing started' >&2
  exit 4
fi
if [[ -n "${DOCKER_HOST:-}" || -n "${DOCKER_CONTEXT:-}" ]]; then
  echo 'S0-E.8 DENIED: Docker endpoint override; nothing started' >&2
  exit 4
fi
command -v docker >/dev/null || { echo 'S0-E.8 DENIED: Docker unavailable'; exit 4; }
command -v python3 >/dev/null || { echo 'S0-E.8 DENIED: Python unavailable'; exit 4; }
command -v sha256sum >/dev/null || { echo 'S0-E.8 DENIED: integrity tool unavailable'; exit 4; }

# Verify everything on disk before any database interaction.
sha256sum -c MANIFEST.sha256 >/dev/null || { echo 'S0-E.8 DENIED: package integrity failure'; exit 4; }
ctx="$(docker context show 2>/dev/null)" || exit 4
[[ "$ctx" == 'default' || "$ctx" == 'desktop-linux' ]] || {
  echo 'S0-E.8 DENIED: unreviewed Docker context'; exit 4;
}
endpoint="$(docker context inspect "$ctx" --format '{{ .Endpoints.docker.Host }}' 2>/dev/null)" || exit 4
[[ "$endpoint" == 'unix:///var/run/docker.sock' ]] || {
  echo 'S0-E.8 DENIED: endpoint not the expected WSL local Unix socket'; exit 4;
}
# A Unix socket does not certify daemon ownership or locality. Human verification
# of the Docker daemon and of the image's provenance is additionally required.
[[ "${S0E8_LOCAL_DAEMON_AND_IMAGE_REVIEWED:-}" == 'YES' ]] || {
  echo 'S0-E.8 DENIED: Docker daemon/image review missing'; exit 4;
}
# Never pull: an explicitly reviewed, pre-existing image must be used.
docker image inspect postgres:16-alpine >/dev/null 2>&1 || {
  echo 'S0-E.8 DENIED: reviewed local postgres:16-alpine image not present'; exit 4;
}

id=''
nonce="$(python3 -c 'import secrets;print(secrets.token_hex(12))')"
label="aetheris.s0e8.nonce=$nonce"
cleanup() {
  if [[ -n "$id" ]]; then
    # Clean up only the exact newly created container with our unique nonce.
    actual="$(docker inspect --format '{{ index .Config.Labels "aetheris.s0e8.nonce" }}' "$id" 2>/dev/null || true)"
    if [[ "$actual" == "$nonce" ]]; then
      docker rm -f "$id" >/dev/null 2>&1 || true
    fi
  fi
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

# Credentials exist in this process only; we never write them to files/logs.
admin_password="$(python3 -c 'import secrets;print(secrets.token_urlsafe(48))')"
container="aetheris-s0e8-pg16-$nonce"
id="$(docker run --detach --rm --pull=never --name "$container" \
  --network none --label "$label" \
  --tmpfs /var/lib/postgresql/data:rw,nosuid,nodev,size=512m \
  -e POSTGRES_DB=s0e7_disposable -e POSTGRES_USER=postgres \
  -e POSTGRES_INITDB_ARGS=--auth-host=scram-sha-256 \
  -e POSTGRES_HOST_AUTH_METHOD=scram-sha-256 \
  -e POSTGRES_PASSWORD="$admin_password" postgres:16-alpine)"
unset admin_password

ready=0
for _ in {1..75}; do
  if docker exec "$id" pg_isready -q -U postgres -d s0e7_disposable >/dev/null 2>&1; then
    ready=1; break
  fi
  sleep 1
done
[[ "$ready" == 1 ]] || { echo 'S0-E.8 FAIL: scratch PostgreSQL not ready'; exit 5; }

admin_sql() {
  # Socket auth is local to THIS disposable container; this never connects to a host DB.
  docker exec -i "$id" psql -X -U postgres -d s0e7_disposable \
     -v ON_ERROR_STOP=1 -q -f -
}
for file in sql/00_bootstrap.sql sql/01_synthetic_tables_grants.sql; do
  admin_sql < "$file" >/dev/null
  printf 'PASS isolated PG16 SQL: %s\n' "$file"
done

# Reject accidental TCP trust/MD5/ident before testing credentials.
hba="$(docker exec "$id" psql -X -U postgres -d s0e7_disposable -Atq \
  -v ON_ERROR_STOP=1 -c "SELECT count(*) > 0 AND bool_and(auth_method = 'scram-sha-256') FROM pg_hba_file_rules WHERE type IN ('host','hostssl','hostnossl')")"
[[ "$hba" == 't' ]] || { echo 'S0-E.8 FAIL: TCP auth not exclusively SCRAM-SHA-256'; exit 6; }
echo 'PASS: all TCP HBA rules require SCRAM-SHA-256'

roles=(identity user audit orchestrator)
declare -A passwords
for role in "${roles[@]}"; do
  passwords["$role"]="$(python3 -c 'import secrets;print(secrets.token_urlsafe(48))')"
done
# Login role names and schema names are fixed constants, not user-supplied.
# Test-only secrets flow through stdin and are never printed or persisted.
{
  echo 'BEGIN;'
  for role in "${roles[@]}"; do
    printf "CREATE ROLE s0e8_%s_login LOGIN INHERIT NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS PASSWORD '%s';\n" \
      "$role" "${passwords[$role]}"
    printf 'GRANT s0e7_%s_runtime TO s0e8_%s_login WITH INHERIT TRUE, SET FALSE;\n' "$role" "$role"
    printf 'GRANT CONNECT ON DATABASE s0e7_disposable TO s0e8_%s_login;\n' "$role"
  done
  echo 'COMMIT;'
} | admin_sql >/dev/null 2>&1 || { echo 'S0-E.8 FAIL: scratch login provisioning failed'; exit 6; }
echo 'PASS: four distinct restricted LOGIN roles provisioned in scratch database'

# Each test is a REAL PostgreSQL TCP session using its own login password.
# Password is passed over stdin, not arguments/environment values of docker CLI.
connect_as() {
  local role="$1" sql="$2" password="${passwords[$role]}"
  printf '%s\n' "$password" | docker exec -i "$id" sh -c \
    'IFS= read -r PGPASSWORD; export PGPASSWORD; exec psql -X -h 127.0.0.1 -p 5432 -U "$1" -d s0e7_disposable -At -v ON_ERROR_STOP=1 -v VERBOSITY=verbose -c "$2"' \
    sh "s0e8_${role}_login" "$sql"
}
pass() { echo "PASS $1"; }
fail() { echo "FAIL $1" >&2; exit 7; }
expect_ok() {
  local role="$1" sql="$2" label="$3"
  connect_as "$role" "$sql" >/dev/null 2>&1 && pass "$label" || fail "$label"
}
expect_result() {
  local role="$1" sql="$2" expected="$3" label="$4" result
  result="$(connect_as "$role" "$sql" 2>/dev/null)" || fail "$label"
  [[ "$result" == "$expected" ]] && pass "$label" || fail "$label"
}
expect_priv_denied() {
  local role="$1" sql="$2" label="$3" output
  if output="$(connect_as "$role" "$sql" 2>&1)"; then
    fail "$label (unexpected privilege)"
  elif [[ "$output" == *'42501'* ]]; then
    pass "$label"
  else
    fail "$label (not a PostgreSQL 42501 permission denial)"
  fi
}

for role in "${roles[@]}"; do
  expect_result "$role" 'SELECT current_user || chr(124) || session_user' \
    "s0e8_${role}_login|s0e8_${role}_login" "$role authenticated as its own identity"
  expect_result "$role" "SELECT count(*) FROM s0e7_${role}.records" '1' \
    "$role reads its own fixture"
  expect_ok "$role" "INSERT INTO s0e7_${role}.records VALUES (2, 'LOGIN_TEST_ONLY')" \
    "$role can insert its own synthetic fixture"
  expect_priv_denied "$role" "CREATE TABLE s0e7_${role}.forbidden_ddl(id bigint)" \
    "$role cannot create tables"
  expect_priv_denied "$role" "ALTER TABLE s0e7_${role}.records ADD COLUMN bad_column text" \
    "$role cannot alter owned tables"
  expect_priv_denied "$role" "CREATE ROLE s0e8_forbidden_${role}" \
    "$role cannot create DB roles"
  expect_priv_denied "$role" "SET ROLE s0e7_${role}_owner" \
    "$role cannot become schema owner"
  expect_priv_denied "$role" "SELECT * FROM pg_authid" \
    "$role cannot read PostgreSQL password catalog"
  for other in "${roles[@]}"; do
    [[ "$role" == "$other" ]] && continue
    expect_priv_denied "$role" "SELECT * FROM s0e7_${other}.records" \
      "$role cannot SELECT $other fixture"
    expect_priv_denied "$role" "INSERT INTO s0e7_${other}.records VALUES (99,'FORBIDDEN')" \
      "$role cannot INSERT $other fixture"
  done
done

expect_ok identity "UPDATE s0e7_identity.records SET synthetic_marker='LOGIN_UPDATED' WHERE id=2" \
  'Identity permitted own UPDATE'
expect_ok user "DELETE FROM s0e7_user.records WHERE id=2" \
  'User permitted own DELETE'
expect_priv_denied audit "UPDATE s0e7_audit.records SET synthetic_marker='DENIED' WHERE id=2" \
  'Audit UPDATE prohibited'
expect_priv_denied audit 'DELETE FROM s0e7_audit.records WHERE id=2' \
  'Audit DELETE prohibited'
expect_priv_denied orchestrator 'DELETE FROM s0e7_orchestrator.records WHERE id=2' \
  'Orchestrator DELETE prohibited'

# Negative credential check against a real authenticated PostgreSQL TCP socket.
wrong="$(python3 -c 'import secrets;print(secrets.token_urlsafe(48))')"
wrong_error=''
if wrong_error="$(printf '%s\n' "$wrong" | docker exec -i "$id" sh -c \
   'IFS= read -r PGPASSWORD; export PGPASSWORD; exec psql -X -h 127.0.0.1 -U s0e8_user_login -d s0e7_disposable -At -v ON_ERROR_STOP=1 -c "SELECT 1"' \
    2>&1)"; then
  fail 'wrong password unexpectedly authenticated'
elif [[ "$wrong_error" == *'password authentication failed'* ]]; then
  pass 'wrong password rejected by PostgreSQL TCP login'
else
  fail 'wrong password test failed for a non-authentication reason'
fi
unset wrong_error
unset wrong

# Confirm the four login roles and eight owner/runtime roles were created with
# restricted role flags. Output is a boolean, never raw password verifier data.
flags="$(docker exec "$id" psql -X -U postgres -d s0e7_disposable -Atq -v ON_ERROR_STOP=1 \
  -c "SELECT (count(*) FILTER (WHERE rolname LIKE 's0e8_%_login' AND rolcanlogin AND NOT rolsuper AND NOT rolcreatedb AND NOT rolcreaterole AND NOT rolbypassrls) = 4) AND (count(*) FILTER (WHERE rolname LIKE 's0e7_%' AND NOT rolcanlogin AND NOT rolsuper AND NOT rolcreatedb AND NOT rolcreaterole AND NOT rolbypassrls) = 8) FROM pg_roles WHERE rolname LIKE 's0e8_%_login' OR rolname LIKE 's0e7_%'")"
[[ "$flags" == 't' ]] && pass 'login/owner/runtime role attribute restrictions' || fail 'role flags invalid'

echo 'S0-E.8 DISPOSABLE AUTHENTICATED PG16 ROLE ISOLATION PASS'
echo 'S0-E.8 RELEASE: NO_GO — NOT A REAL SERVICE OR HISTORIC DATABASE TEST'
# trap removes only the exact newly labeled disposable container.
