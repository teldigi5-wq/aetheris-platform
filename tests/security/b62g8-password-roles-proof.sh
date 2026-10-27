#!/usr/bin/env bash
# B62-G8 test-only true password-authenticated PostgreSQL role proof.
# CI runner only; never run alongside a real DB, tenant, or production keys.
set -euo pipefail
cd "$(dirname "$0")/../.."
COMPOSE=(docker compose -f tests/security/docker-compose.b62g-epoch-proof.yml)
cleanup() {
  "${COMPOSE[@]}" down --volumes --remove-orphans --timeout 10 >/dev/null 2>&1 || true
}
trap cleanup EXIT
"${COMPOSE[@]}" config --format json | python3 -c '
import sys,json
d=json.load(sys.stdin)
assert set(d["services"]) == {"postgres"}
assert len(d.get("networks",{})) == 1
assert all(net.get("internal") is True for net in d["networks"].values())
assert len(d.get("volumes",{})) == 1
assert not any(v.get("external") for v in d["volumes"].values())
p=d["services"]["postgres"]
assert not p.get("ports") and not p.get("privileged") and not p.get("devices")
assert not any("docker.sock" in str(v) for v in p.get("volumes",[]))
assert p["environment"]["POSTGRES_DB"] == "b62g_synthetic"
assert p["environment"]["POSTGRES_USER"] == "b62g_synthetic"
assert p["environment"]["POSTGRES_PASSWORD"] == "test-only-b62g-never-use-in-production"
print("B62G8_PRIVATE_DISPOSABLE_POSTGRES_TOPOLOGY")
'

"${COMPOSE[@]}" up -d --wait --wait-timeout 120 postgres
ADMIN=(psql -X -qAt -v ON_ERROR_STOP=1 -U b62g_synthetic -d b62g_synthetic)
"${COMPOSE[@]}" exec -T postgres "${ADMIN[@]}" < tests/security/b62g-revocation-prototype.sql
"${COMPOSE[@]}" exec -T postgres "${ADMIN[@]}" < tests/security/b62g-policy-assertions.sql
"${COMPOSE[@]}" exec -T postgres "${ADMIN[@]}" < tests/security/b62g2-readonly-role.sql
"${COMPOSE[@]}" exec -T postgres "${ADMIN[@]}" < tests/security/b62g8-login-roles.sql

# Force real TCP/password authentication. Neither SET ROLE nor the synthetic
# database owner is used to make the admission or emergency-write decisions.
user_pass() {
  case "$1" in
    b62g8_gateway_reader) printf 'b62g8-gateway-synthetic-ci-only' ;;
    b62g8_user_reader) printf 'b62g8-user-synthetic-ci-only' ;;
    b62g8_audit_reader) printf 'b62g8-audit-synthetic-ci-only' ;;
    b62g8_emergency_writer) printf 'b62g8-writer-synthetic-ci-only' ;;
    *) return 1 ;;
  esac
}
authenticated_sql() {
  local role="$1" query="$2" pass
  pass="$(user_pass "$role")"
  "${COMPOSE[@]}" exec -T -e "PGPASSWORD=${pass}" postgres \
    timeout 5s psql -X -qAt -v ON_ERROR_STOP=1 \
      -h 127.0.0.1 -p 5432 -U "$role" -d b62g_synthetic -c "$query"
}
expect_forbidden() {
  local role="$1" query="$2"
  if authenticated_sql "$role" "$query" >/dev/null 2>&1; then
    echo "B62G8_UNAUTHORIZED_SQL_SUCCEEDED role=${role}"
    exit 1
  fi
}
expect_signed_decision() {
  local role="$1" uid="$2" kid="$3" iat="$4" expected="$5" observed
  # Only strictly known fixture inputs are interpolated, never HTTP data.
  if ! observed="$(authenticated_sql "$role" \
    "SELECT b62g_proof.synthetic_post_signature_allowed(${uid},'b62g-identity','${kid}',${iat});" 2>/dev/null)"; then
    observed="ERROR"
  fi
  local decision="DENY"
  [[ "$observed" == "t" ]] && decision="ALLOW"
  if [[ "$decision" != "$expected" ]]; then
    echo "B62G8_INCORRECT_SIGNED_DECISION role=${role} expected=${expected} actual=${decision}"
    exit 1
  fi
}
READERS=(b62g8_gateway_reader b62g8_user_reader b62g8_audit_reader)
for reader in "${READERS[@]}"; do
  # Signed JWT checking is established by B62-G3..G7 separately; SQL input
  # here is an explicitly pinned synthetic post-signature claim tuple.
  expect_signed_decision "$reader" 42 key-current 5001 ALLOW
  expect_signed_decision "$reader" 42 key-current 5000 DENY
  expect_signed_decision "$reader" 43 key-current 5000 ALLOW
  expect_forbidden "$reader" "SELECT b62g_proof.raise_principal_epoch(42,8000);"
  expect_forbidden "$reader" "SELECT b62g_proof.block_signer('b62g-identity','key-current','emergency');"
  expect_forbidden "$reader" "DELETE FROM b62g_proof.principal_epochs WHERE principal_id=42;"
  expect_forbidden "$reader" "UPDATE b62g_proof.principal_epochs SET reject_issued_at_or_before_epoch=1 WHERE principal_id=42;"
  expect_forbidden "$reader" "CREATE TABLE b62g_proof.unauthorized_write(id int);"
done
echo B62G8_THREE_DISTINCT_AUTHENTICATED_READERS_CANNOT_WRITE

# This credential is NOT the synthetic DB superuser or verifier role.
expect_forbidden b62g8_emergency_writer \
  "SELECT b62g_proof.synthetic_post_signature_allowed(42,'b62g-identity','key-current',5001);"
expect_forbidden b62g8_emergency_writer \
  "DELETE FROM b62g_proof.blocked_signer_kids WHERE kid='key-current';"
expect_forbidden b62g8_emergency_writer "CREATE TABLE b62g_proof.unreviewed(id int);"

# Exercise separate, real password-authenticated emergency writer sessions.
authenticated_sql b62g8_emergency_writer \
  "SELECT b62g_proof.raise_principal_epoch(42,12000);" >/dev/null
for reader in "${READERS[@]}"; do
  expect_signed_decision "$reader" 42 key-current 11999 DENY
  expect_signed_decision "$reader" 42 key-current 12000 DENY
  expect_signed_decision "$reader" 42 key-current 12001 ALLOW
  expect_signed_decision "$reader" 43 key-current 12000 ALLOW
done
echo B62G8_PRIMARY_READ_AFTER_COMMITTED_WRITER_UPDATE_PASS

pids=()
for cutoff in 13000 12500 13999 14000 13500 10000 12001 8000; do
  authenticated_sql b62g8_emergency_writer \
    "SELECT b62g_proof.raise_principal_epoch(42,${cutoff});" >/dev/null &
  pids+=("$!")
done
for pid in "${pids[@]}"; do
  wait "$pid"
done
for reader in "${READERS[@]}"; do
  expect_signed_decision "$reader" 42 key-current 13999 DENY
  expect_signed_decision "$reader" 42 key-current 14000 DENY
  expect_signed_decision "$reader" 42 key-current 14001 ALLOW
done
echo B62G8_PARALLEL_PASSWORD_WRITERS_MONOTONIC_CUTOFF_PASS

authenticated_sql b62g8_emergency_writer \
  "SELECT b62g_proof.block_signer('b62g-identity','key-current','compromise');" >/dev/null
for reader in "${READERS[@]}"; do
  expect_signed_decision "$reader" 42 key-current 14001 DENY
  expect_signed_decision "$reader" 43 key-current 14001 DENY
done
echo B62G8_SIGNER_BLOCK_VISIBLE_TO_THREE_AUTHENTICATED_READERS_PASS

"${COMPOSE[@]}" restart postgres
"${COMPOSE[@]}" up -d --wait --wait-timeout 120 postgres
for reader in "${READERS[@]}"; do
  expect_signed_decision "$reader" 42 key-current 14001 DENY
  expect_signed_decision "$reader" 43 key-current 14001 DENY
done
echo B62G8_SYNTHETIC_ROLES_AND_DENIAL_SURVIVE_POSTGRES_RESTART

"${COMPOSE[@]}" stop postgres
for reader in "${READERS[@]}"; do
  expect_signed_decision "$reader" 43 key-current 14001 DENY
done
echo B62G8_STOPPED_PRIMARY_FAILS_CLOSED_IN_TEST_CLIENT
echo B62G8_REAL_POSTGRES_LOGIN_PROOF_ONLY_NO_PRODUCTION_INTEGRATION
