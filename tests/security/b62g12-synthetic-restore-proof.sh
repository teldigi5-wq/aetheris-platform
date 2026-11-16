#!/usr/bin/env bash
# B62-G12: review-only REAL synthetic pg_dump/pg_restore evidence.
# Primary production schemas, Flyway history and user data are NEVER touched.
set -euo pipefail
cd "$(dirname "$0")/../.."
COMPOSE=(docker compose
 -f tests/security/docker-compose.b62g-epoch-proof.yml
 -f tests/security/b62g12-restore-overlay.yml)
cleanup() {
  "${COMPOSE[@]}" down --volumes --remove-orphans --timeout 10 >/dev/null 2>&1 || true
}
trap cleanup EXIT

"${COMPOSE[@]}" config --format json | python3 -c '
import sys,json
d=json.load(sys.stdin)
assert set(d["services"])=={"postgres","auth-probe"}
assert len(d["networks"])==1 and all(v.get("internal") is True for v in d["networks"].values())
assert len(d.get("volumes",{}))==1 and not any(v.get("external") for v in d["volumes"].values())
for name,service in d["services"].items():
    assert not service.get("ports") and not service.get("privileged"),name
    assert not service.get("devices") and not service.get("network_mode"),name
    assert not any("docker.sock" in str(v) for v in service.get("volumes",[])),name
assert d["services"]["postgres"]["environment"]["POSTGRES_DB"]=="b62g_synthetic"
assert d["services"]["postgres"]["environment"]["POSTGRES_PASSWORD"]=="test-only-b62g-never-use-in-production"
p=d["services"]["auth-probe"]
assert not p.get("environment") and not p.get("volumes")
assert "ALL" in {str(x).upper() for x in p.get("cap_drop",[])}
assert "no-new-privileges:true" in p.get("security_opt",[])
print("B62G12_DISPOSABLE_NO_PORT_POSTGRES_CLIENT_BOUNDARY",flush=True)
'
"${COMPOSE[@]}" up -d --wait --wait-timeout 120 postgres auth-probe

ADMIN=(psql -X -qAt -v ON_ERROR_STOP=1 -U b62g_synthetic -d b62g_synthetic)
admin_sql() { "${COMPOSE[@]}" exec -T postgres "${ADMIN[@]}" -c "$1"; }
for file in b62g-revocation-prototype.sql b62g-policy-assertions.sql \
    b62g2-readonly-role.sql b62g8-login-roles.sql; do
  "${COMPOSE[@]}" exec -T postgres "${ADMIN[@]}" < "tests/security/$file"
done

reader_password() {
  case "$1" in
    b62g8_gateway_reader) printf '%s' 'b62g8-gateway-synthetic-ci-only' ;;
    b62g8_user_reader) printf '%s' 'b62g8-user-synthetic-ci-only' ;;
    b62g8_audit_reader) printf '%s' 'b62g8-audit-synthetic-ci-only' ;;
    *) return 1 ;;
  esac
}
reader_sql() {
  local role="$1" db="$2" uid="$3" kid="$4" iat="$5" password
  case "$db" in b62g_synthetic|b62g12_stale|b62g12_latest) ;; *) return 2;; esac
  case "$uid" in 42|43) ;; *) return 2;; esac
  case "$kid" in key-current|key-safe) ;; *) return 2;; esac
  case "$iat" in 11000|12000|12001|14001) ;; *) return 2;; esac
  password="$(reader_password "$role")"
  "${COMPOSE[@]}" exec -T -e "PGPASSWORD=$password" -e PGCONNECT_TIMEOUT=2 \
    auth-probe timeout 7s psql -X -qAt -w -v ON_ERROR_STOP=1 \
      -h postgres -p 5432 -U "$role" -d "$db" -c \
      "SELECT b62g_proof.synthetic_post_signature_allowed($uid,'b62g-identity','$kid',$iat);"
}
expect_sql() {
  local role="$1" db="$2" uid="$3" kid="$4" iat="$5" expected="$6" actual
  # A connection error is NOT an acceptable synthetic DENY assertion:
  # require genuine, authenticated, SQL boolean response from the restore.
  actual="$(reader_sql "$role" "$db" "$uid" "$kid" "$iat")" || {
    echo "B62G12_READER_QUERY_FAILED ${role} ${db}"; exit 1;
  }
  [[ "$actual" == "$expected" ]] || {
    echo "B62G12_UNEXPECTED_RESTORED_POLICY ${role} ${db}"; exit 1;
  }
}
readers=(b62g8_gateway_reader b62g8_user_reader b62g8_audit_reader)
for role in "${readers[@]}"; do
  expect_sql "$role" b62g_synthetic 42 key-current 11000 t
  expect_sql "$role" b62g_synthetic 43 key-current 14001 t
done

# Backup a deliberately STALE pre-incident state. Explicitly never treat
# this snapshot as an authorized post-incident restore.
"${COMPOSE[@]}" exec -T postgres sh -ec \
  'pg_dump -U b62g_synthetic -d b62g_synthetic -n b62g_proof -Fc --no-owner -f /tmp/b62g12-stale.dump && test -s /tmp/b62g12-stale.dump'
echo B62G12_PRE_REVOCATION_BACKUP_CREATED_DISPOSABLE_ONLY

writer_sql() {
  "${COMPOSE[@]}" exec -T -e 'PGPASSWORD=b62g8-writer-synthetic-ci-only' -e PGCONNECT_TIMEOUT=2 \
    auth-probe timeout 7s psql -X -qAt -w -v ON_ERROR_STOP=1 \
      -h postgres -U b62g8_emergency_writer -d b62g_synthetic -c "$1"
}
writer_sql "SELECT b62g_proof.raise_principal_epoch(42,12000);" >/dev/null
writer_sql "SELECT b62g_proof.block_signer('b62g-identity','key-current','compromise');" >/dev/null
for role in "${readers[@]}"; do
  expect_sql "$role" b62g_synthetic 42 key-current 12001 f
  expect_sql "$role" b62g_synthetic 43 key-current 14001 f
  expect_sql "$role" b62g_synthetic 42 key-safe 11000 f
  expect_sql "$role" b62g_synthetic 42 key-safe 12001 t
  expect_sql "$role" b62g_synthetic 43 key-safe 14001 t
done
echo B62G12_AUTHENTICATED_WRITER_COMMITTED_REVOCATION_STATE

"${COMPOSE[@]}" exec -T postgres sh -ec \
  'pg_dump -U b62g_synthetic -d b62g_synthetic -n b62g_proof -Fc --no-owner -f /tmp/b62g12-latest.dump && test -s /tmp/b62g12-latest.dump'

# All restore operations occur in two newly created, uniquely named
# disposable CI databases. Original source DB is never restored over.
for db in b62g12_stale b62g12_latest; do
  "${COMPOSE[@]}" exec -T postgres createdb -U b62g_synthetic "$db"
  admin_sql "REVOKE CONNECT ON DATABASE $db FROM PUBLIC;"
  admin_sql "GRANT CONNECT ON DATABASE $db TO b62g8_gateway_reader,b62g8_user_reader,b62g8_audit_reader;"
done
"${COMPOSE[@]}" exec -T postgres pg_restore -U b62g_synthetic -d b62g12_stale \
  --exit-on-error --no-owner /tmp/b62g12-stale.dump
"${COMPOSE[@]}" exec -T postgres pg_restore -U b62g_synthetic -d b62g12_latest \
  --exit-on-error --no-owner /tmp/b62g12-latest.dump
echo B62G12_TWO_REAL_PG_RESTORES_COMPLETED

for role in "${readers[@]}"; do
  # These decisions MUST have different values: the older backup permits
  # revoked credentials that the latest authenticated restore denies.
  expect_sql "$role" b62g12_stale 42 key-current 11000 t
  expect_sql "$role" b62g12_stale 43 key-current 14001 t
  expect_sql "$role" b62g12_latest 42 key-current 11000 f
  expect_sql "$role" b62g12_latest 43 key-current 14001 f
  expect_sql "$role" b62g12_latest 42 key-safe 11000 f
  expect_sql "$role" b62g12_latest 42 key-safe 12000 f
  expect_sql "$role" b62g12_latest 42 key-safe 12001 t
  expect_sql "$role" b62g12_latest 43 key-safe 14001 t
done
echo B62G12_LATEST_RESTORE_PRESERVES_EPOCH_AND_BLOCKED_SIGNER
echo B62G12_STALE_PRE_REVOCATION_RESTORE_UNSAFE_ALLOW_REJECTED_BY_POLICY

# Prove actual separate-network authentication and least privileges, not
# local localhost trust auth (known limitation in old B62-G8 script).
if "${COMPOSE[@]}" exec -T -e 'PGPASSWORD=deliberately-wrong-password' -e PGCONNECT_TIMEOUT=2 \
    auth-probe timeout 5s psql -X -qAt -w -v ON_ERROR_STOP=1 \
      -h postgres -U b62g8_gateway_reader -d b62g12_latest -c 'SELECT 1;' >/dev/null 2>&1; then
  echo B62G12_WRONG_PASSWORD_ACCEPTED
  exit 1
fi
for role in "${readers[@]}"; do
  password="$(reader_password "$role")"
  if "${COMPOSE[@]}" exec -T -e "PGPASSWORD=$password" -e PGCONNECT_TIMEOUT=2 \
      auth-probe timeout 5s psql -X -qAt -w -v ON_ERROR_STOP=1 \
      -h postgres -U "$role" -d b62g12_latest \
      -c "UPDATE b62g_proof.principal_epochs SET reject_issued_at_or_before_epoch=1 WHERE principal_id=42;" >/dev/null 2>&1; then
    echo B62G12_READ_ONLY_ROLE_UPDATED_REVOCATION_AFTER_RESTORE
    exit 1
  fi
done
# Writer is NOT granted CONNECT to either restored DB: restoration cannot
# silently enable emergency writes without a new authorized review.
if "${COMPOSE[@]}" exec -T -e 'PGPASSWORD=b62g8-writer-synthetic-ci-only' \
    auth-probe timeout 5s psql -X -qAt -w -v ON_ERROR_STOP=1 \
    -h postgres -U b62g8_emergency_writer -d b62g12_latest -c 'SELECT 1;' >/dev/null 2>&1; then
  echo B62G12_UNAUTHORIZED_EMERGENCY_WRITER_REACHED_RESTORE
  exit 1
fi
echo B62G12_RESTORED_DB_READERS_AUTHENTICATED_AND_WRITE_DENIED
echo B62G12_RESTORED_DB_EMERGENCY_WRITER_UNAUTHORIZED
echo B62G12_PROOF_ONLY_HISTORICAL_PRODUCTION_MIGRATION_STILL_NO_GO
